package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.lab.tfidf.local.LocalTfidfProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TfidfJoinJobTest {

    private static final double TOLERANCE = 1e-9;

    @TempDir
    java.nio.file.Path temporaryDirectory;

    @Test
    void reducerStreamsDocumentsAndReportsMissingOrInvalidValues() throws Exception {
        java.nio.file.Path tfInput = write(
                "join/tf/input.tsv",
                List.of(
                        "doc-a:hadoop\t0.5",
                        "doc-b:hadoop\t0.25",
                        "doc-c:orphan\t0.5",
                        "doc-d:bad-idf\t0.5",
                        "doc-e:bad-tf\tnot-a-number",
                        "malformed-tf-record"
                )
        );
        java.nio.file.Path idfInput = write(
                "join/idf/input.tsv",
                List.of(
                        "hadoop\t0.4",
                        "bad-idf\tnot-a-number",
                        "bad-tf\t0.3"
                )
        );
        java.nio.file.Path output = temporaryDirectory.resolve("join/output");

        Job job = TfidfJoinJob.create(
                localConfiguration(),
                new Path(tfInput.toUri()),
                new Path(idfInput.toUri()),
                new Path(output.toUri())
        );

        assertTrue(job.waitForCompletion(false));

        Map<String, Double> actual = readOutput(output);
        assertEquals(2, actual.size());
        assertEquals(0.2, actual.get("doc-a:hadoop"), TOLERANCE);
        assertEquals(0.1, actual.get("doc-b:hadoop"), TOLERANCE);
        assertEquals(1, counter(job, "MISSING_IDF"));
        assertEquals(1, counter(job, "INVALID_IDF_VALUES"));
        assertEquals(1, counter(job, "INVALID_TF_VALUES"));
        assertEquals(1, counter(job, "INVALID_TF_RECORDS"));
    }

    @Test
    void optimizedPipelineMatchesExpectedRegressionCorpus() throws Exception {
        java.nio.file.Path input = write(
                "pipeline/input.tsv",
                List.of(
                        "<urn:uuid:doc-001>\thadoop spark hadoop mapreduce",
                        "doc-002\tspark java spark",
                        "doc-003\thadoop java"
                )
        );
        java.nio.file.Path tfOutput = temporaryDirectory.resolve("pipeline/tf");
        java.nio.file.Path idfOutput = temporaryDirectory.resolve("pipeline/idf");
        java.nio.file.Path tfidfOutput = temporaryDirectory.resolve("pipeline/tfidf");
        Configuration configuration = localConfiguration();

        Job tfJob = TfJob.create(
                configuration,
                new Path(input.toUri()),
                new Path(tfOutput.toUri())
        );
        assertTrue(tfJob.waitForCompletion(false));

        long documents = tfJob.getCounters()
                .findCounter("TF", "PROCESSED_DOCUMENTS")
                .getValue();
        assertEquals(3, documents);

        Job idfJob = IdfJob.create(
                configuration,
                new Path(tfOutput.toUri()),
                new Path(idfOutput.toUri()),
                documents
        );
        assertTrue(idfJob.waitForCompletion(false));

        Job joinJob = TfidfJoinJob.create(
                configuration,
                new Path(new Path(tfOutput.toUri()), "part-m-*"),
                new Path(new Path(idfOutput.toUri()), "part-r-*"),
                new Path(tfidfOutput.toUri())
        );
        assertTrue(joinJob.waitForCompletion(false));

        Map<String, Double> expected = Map.of(
                "<urn:uuid:doc-001>:hadoop", 0.2027325540540822,
                "<urn:uuid:doc-001>:spark", 0.1013662770270411,
                "<urn:uuid:doc-001>:mapreduce", 0.2746530721670274,
                "doc-002:spark", 0.2703100720721096,
                "doc-002:java", 0.1351550360360548,
                "doc-003:hadoop", 0.2027325540540822,
                "doc-003:java", 0.2027325540540822
        );
        Map<String, Double> actual = readOutput(tfidfOutput);

        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((key, value) ->
                assertEquals(value, actual.get(key), TOLERANCE, key));

        java.nio.file.Path localOutput = temporaryDirectory.resolve("pipeline/local");
        new LocalTfidfProcessor().process(input, localOutput, false);
        Map<String, Double> local = readFile(localOutput.resolve("tfidf.tsv"));
        assertEquals(actual.keySet(), local.keySet());
        actual.forEach((key, value) ->
                assertEquals(value, local.get(key), TOLERANCE, key));
    }

    private Configuration localConfiguration() {
        Configuration configuration = new Configuration();
        configuration.set("mapreduce.framework.name", "local");
        configuration.set("fs.defaultFS", "file:///");
        configuration.set("hadoop.tmp.dir",
                temporaryDirectory.resolve("hadoop-tmp").toString());
        return configuration;
    }

    private java.nio.file.Path write(String relativePath, List<String> lines)
            throws IOException {
        java.nio.file.Path file = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static Map<String, Double> readOutput(java.nio.file.Path output)
            throws IOException {
        Map<String, Double> result = new HashMap<>();

        try (Stream<java.nio.file.Path> files = Files.list(output)) {
            for (java.nio.file.Path file : files
                    .filter(path -> path.getFileName().toString().startsWith("part-"))
                    .toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int separator = line.indexOf('\t');
                    result.put(
                            line.substring(0, separator),
                            Double.parseDouble(line.substring(separator + 1))
                    );
                }
            }
        }

        return result;
    }

    private static Map<String, Double> readFile(java.nio.file.Path file)
            throws IOException {
        Map<String, Double> result = new HashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('\t');
            result.put(
                    line.substring(0, separator),
                    Double.parseDouble(line.substring(separator + 1))
            );
        }
        return result;
    }

    private static long counter(Job job, String name) throws IOException {
        return job.getCounters().findCounter("JOIN", name).getValue();
    }
}
