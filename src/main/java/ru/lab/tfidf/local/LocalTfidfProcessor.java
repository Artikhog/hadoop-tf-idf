package ru.lab.tfidf.local;

import ru.lab.tfidf.common.Tokenizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class LocalTfidfProcessor {

    private static final String TF_FILE = "tf.tsv";
    private static final String IDF_FILE = "idf.tsv";
    private static final String TFIDF_FILE = "tfidf.tsv";
    private static final String STATISTICS_FILE = "statistics.json";
    private static final int HEAP_SAMPLE_INTERVAL = 4_096;

    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private int recordsSinceHeapSample;

    public LocalTfidfStatistics process(Path input, Path output, boolean overwrite)
            throws IOException {
        long totalStart = System.nanoTime();
        LocalTfidfStatistics statistics = new LocalTfidfStatistics();
        recordsSinceHeapSample = 0;
        sampleHeap(statistics);

        Path normalizedInput = input.toAbsolutePath().normalize();
        Path normalizedOutput = output.toAbsolutePath().normalize();
        validateInput(normalizedInput);
        validateOutputLocation(normalizedInput, normalizedOutput);
        List<Path> inputFiles = inputFiles(normalizedInput);
        for (Path file : inputFiles) {
            statistics.inputBytes += Files.size(file);
        }

        prepareOutput(normalizedOutput, overwrite);

        Map<String, WordStatistics> words = new TreeMap<>();
        Path tfFile = normalizedOutput.resolve(TF_FILE);
        Path idfFile = normalizedOutput.resolve(IDF_FILE);
        Path tfidfFile = normalizedOutput.resolve(TFIDF_FILE);

        long stageStart = System.nanoTime();
        writeTf(inputFiles, tfFile, words, statistics);
        statistics.tfDurationMs = elapsedMillis(stageStart);
        statistics.uniqueWords = words.size();
        sampleHeap(statistics);

        stageStart = System.nanoTime();
        writeIdf(idfFile, words, statistics.processedDocuments, statistics);
        statistics.idfDurationMs = elapsedMillis(stageStart);
        sampleHeap(statistics);

        stageStart = System.nanoTime();
        writeTfidf(tfFile, tfidfFile, words, statistics);
        statistics.joinDurationMs = elapsedMillis(stageStart);
        statistics.totalDurationMs = elapsedMillis(totalStart);
        sampleHeap(statistics);
        statistics.writeJson(normalizedOutput.resolve(STATISTICS_FILE));
        return statistics;
    }

    private void writeTf(
            List<Path> inputFiles,
            Path tfFile,
            Map<String, WordStatistics> words,
            LocalTfidfStatistics statistics
    ) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(tfFile, StandardCharsets.UTF_8)) {
            for (Path inputFile : inputFiles) {
                try (BufferedReader reader = Files.newBufferedReader(
                        inputFile, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processDocument(line, writer, words, statistics);
                        sampleHeapPeriodically(statistics);
                    }
                }
            }
        }
    }

    private void processDocument(
            String line,
            BufferedWriter writer,
            Map<String, WordStatistics> words,
            LocalTfidfStatistics statistics
    ) throws IOException {
        int separator = line.indexOf('\t');
        if (separator <= 0 || separator == line.length() - 1) {
            statistics.invalidDocuments++;
            return;
        }

        String documentId = line.substring(0, separator);
        List<String> tokens = Tokenizer.tokenize(line.substring(separator + 1));
        if (tokens.isEmpty()) {
            statistics.emptyDocuments++;
            return;
        }

        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String token : tokens) {
            frequencies.merge(token, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            double tf = (double) entry.getValue() / tokens.size();
            writer.write(documentId);
            writer.write(':');
            writer.write(entry.getKey());
            writer.write('\t');
            writer.write(Double.toString(tf));
            writer.newLine();
            words.computeIfAbsent(entry.getKey(), ignored -> new WordStatistics())
                    .documentFrequency++;
            statistics.tfRecords++;
        }

        statistics.processedDocuments++;
        statistics.totalTokens += tokens.size();
    }

    private void writeIdf(
            Path idfFile,
            Map<String, WordStatistics> words,
            long totalDocuments,
            LocalTfidfStatistics statistics
    ) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(idfFile, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, WordStatistics> entry : words.entrySet()) {
                WordStatistics word = entry.getValue();
                word.idf = Math.log((double) totalDocuments / word.documentFrequency);
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(Double.toString(word.idf));
                writer.newLine();
                sampleHeapPeriodically(statistics);
            }
        }
    }

    private void writeTfidf(
            Path tfFile,
            Path tfidfFile,
            Map<String, WordStatistics> words,
            LocalTfidfStatistics statistics
    ) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(tfFile, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(tfidfFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                int colon = line.lastIndexOf(':', tab);
                if (tab < 0 || colon <= 0 || colon == tab - 1) {
                    throw new IOException("Invalid generated TF record: " + line);
                }

                String word = line.substring(colon + 1, tab);
                WordStatistics wordStatistics = words.get(word);
                if (wordStatistics == null) {
                    throw new IOException("Missing IDF for word: " + word);
                }

                double tf;
                try {
                    tf = Double.parseDouble(line.substring(tab + 1));
                } catch (NumberFormatException exception) {
                    throw new IOException("Invalid generated TF value: " + line, exception);
                }

                writer.write(line, 0, tab + 1);
                writer.write(Double.toString(tf * wordStatistics.idf));
                writer.newLine();
                statistics.tfidfRecords++;
                sampleHeapPeriodically(statistics);
            }
        }
    }

    private static void validateInput(Path input) throws IOException {
        if (!Files.exists(input)) {
            throw new IOException("Input does not exist: " + input);
        }
        if (!Files.isRegularFile(input) && !Files.isDirectory(input)) {
            throw new IOException("Input must be a file or directory: " + input);
        }
    }

    private static void validateOutputLocation(Path input, Path output) throws IOException {
        Path resolvedInput = input.toRealPath();
        Path resolvedOutput = resolveThroughExistingParent(output);
        if (resolvedInput.equals(resolvedOutput) || resolvedInput.startsWith(resolvedOutput)) {
            throw new IOException("Output must not contain the input: " + output);
        }
    }

    private static Path resolveThroughExistingParent(Path path) throws IOException {
        Path existing = path;
        while (!Files.exists(existing)) {
            existing = existing.getParent();
        }
        return existing.toRealPath().resolve(existing.relativize(path)).normalize();
    }

    private static List<Path> inputFiles(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }
        try (Stream<Path> paths = Files.list(input)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".tsv"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static void prepareOutput(Path output, boolean overwrite) throws IOException {
        if (Files.exists(output)) {
            if (!overwrite) {
                throw new FileAlreadyExistsException(output.toString());
            }
            try (Stream<Path> paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(output);
    }

    private void sampleHeap(LocalTfidfStatistics statistics) {
        statistics.peakHeapUsedBytes = Math.max(
                statistics.peakHeapUsedBytes,
                memory.getHeapMemoryUsage().getUsed()
        );
    }

    private void sampleHeapPeriodically(LocalTfidfStatistics statistics) {
        if (++recordsSinceHeapSample == HEAP_SAMPLE_INTERVAL) {
            sampleHeap(statistics);
            recordsSinceHeapSample = 0;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static final class WordStatistics {
        private long documentFrequency;
        private double idf;
    }
}
