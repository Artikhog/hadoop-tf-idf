package ru.lab.tfidf.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTfidfProcessorTest {

    private static final double TOLERANCE = 1e-9;

    @TempDir
    Path temporaryDirectory;

    @Test
    void computesAllStagesAndSkipsInvalidAndEmptyDocuments() throws Exception {
        Path input = write("input.tsv", List.of(
                "doc-001\thadoop spark hadoop mapreduce",
                "doc-002\tspark java spark",
                "doc-003\thadoop java",
                "missing-tab",
                "\tmissing-id",
                "missing-text\t",
                "empty\t123 !!!"
        ));
        Path output = temporaryDirectory.resolve("result");

        LocalTfidfStatistics statistics = new LocalTfidfProcessor()
                .process(input, output, false);

        assertValues(output.resolve("tf.tsv"), Map.of(
                "doc-001:hadoop", 0.5,
                "doc-001:spark", 0.25,
                "doc-001:mapreduce", 0.25,
                "doc-002:spark", 2.0 / 3.0,
                "doc-002:java", 1.0 / 3.0,
                "doc-003:hadoop", 0.5,
                "doc-003:java", 0.5
        ));
        assertValues(output.resolve("idf.tsv"), Map.of(
                "hadoop", Math.log(3.0 / 2.0),
                "spark", Math.log(3.0 / 2.0),
                "java", Math.log(3.0 / 2.0),
                "mapreduce", Math.log(3.0)
        ));
        assertValues(output.resolve("tfidf.tsv"), Map.of(
                "doc-001:hadoop", 0.2027325540540822,
                "doc-001:spark", 0.1013662770270411,
                "doc-001:mapreduce", 0.2746530721670274,
                "doc-002:spark", 0.2703100720721096,
                "doc-002:java", 0.1351550360360548,
                "doc-003:hadoop", 0.2027325540540822,
                "doc-003:java", 0.2027325540540822
        ));

        assertEquals(Files.size(input), statistics.getInputBytes());
        assertEquals(3, statistics.getProcessedDocuments());
        assertEquals(3, statistics.getInvalidDocuments());
        assertEquals(1, statistics.getEmptyDocuments());
        assertEquals(9, statistics.getTotalTokens());
        assertEquals(4, statistics.getUniqueWords());
        assertEquals(7, statistics.getTfRecords());
        assertEquals(7, statistics.getTfidfRecords());
        assertTrue(statistics.getPeakHeapUsedBytes() > 0);

        String json = Files.readString(
                output.resolve("statistics.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"processedDocuments\": 3"));
        assertTrue(json.contains("\"uniqueWords\": 4"));
        assertTrue(json.contains("\"peakHeapUsedBytes\":"));
    }

    @Test
    void processesDirectoryTsvFilesInDeterministicOrder() throws Exception {
        Path input = Files.createDirectory(temporaryDirectory.resolve("inputs"));
        Files.writeString(input.resolve("b.tsv"), "doc-b\tbeta\n", StandardCharsets.UTF_8);
        Files.writeString(input.resolve("a.tsv"), "doc-a\talpha\n", StandardCharsets.UTF_8);
        Files.writeString(input.resolve("ignored.txt"), "doc-x\txray\n", StandardCharsets.UTF_8);
        Path output = temporaryDirectory.resolve("result");

        LocalTfidfStatistics statistics = new LocalTfidfProcessor()
                .process(input, output, false);

        assertEquals(List.of("doc-a:alpha\t1.0", "doc-b:beta\t1.0"),
                Files.readAllLines(output.resolve("tf.tsv"), StandardCharsets.UTF_8));
        assertEquals(2, statistics.getProcessedDocuments());
        assertEquals(
                Files.size(input.resolve("a.tsv")) + Files.size(input.resolve("b.tsv")),
                statistics.getInputBytes()
        );
    }

    @Test
    void refusesExistingOutputUnlessOverwriteIsEnabled() throws Exception {
        Path input = write("input.tsv", List.of("doc\thadoop"));
        Path output = temporaryDirectory.resolve("result");
        LocalTfidfProcessor processor = new LocalTfidfProcessor();
        processor.process(input, output, false);
        Path marker = Files.writeString(output.resolve("marker"), "keep");

        assertThrows(FileAlreadyExistsException.class,
                () -> processor.process(input, output, false));
        assertTrue(Files.exists(marker));

        processor.process(input, output, true);
        assertFalse(Files.exists(marker));
        assertTrue(Files.exists(output.resolve("tfidf.tsv")));
    }

    @Test
    void rejectsMissingInput() {
        Path missing = temporaryDirectory.resolve("missing.tsv");
        Path output = temporaryDirectory.resolve("result");

        IOException exception = assertThrows(IOException.class,
                () -> new LocalTfidfProcessor().process(missing, output, false));

        assertTrue(exception.getMessage().contains("Input does not exist"));
        assertFalse(Files.exists(output));
    }

    private Path write(String relativePath, List<String> lines) throws IOException {
        Path file = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static void assertValues(Path file, Map<String, Double> expected)
            throws IOException {
        Map<String, Double> actual = readValues(file);
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((key, value) ->
                assertEquals(value, actual.get(key), TOLERANCE, key));
    }

    private static Map<String, Double> readValues(Path file) throws IOException {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('\t');
            values.put(
                    line.substring(0, separator),
                    Double.parseDouble(line.substring(separator + 1))
            );
        }
        return values;
    }
}
