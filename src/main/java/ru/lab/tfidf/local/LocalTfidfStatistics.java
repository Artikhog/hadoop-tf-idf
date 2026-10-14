package ru.lab.tfidf.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalTfidfStatistics {

    long inputBytes;
    long processedDocuments;
    long invalidDocuments;
    long emptyDocuments;
    long totalTokens;
    long uniqueWords;
    long tfRecords;
    long tfidfRecords;
    long tfDurationMs;
    long idfDurationMs;
    long joinDurationMs;
    long totalDurationMs;
    long peakHeapUsedBytes;

    public long getInputBytes() {
        return inputBytes;
    }

    public long getProcessedDocuments() {
        return processedDocuments;
    }

    public long getInvalidDocuments() {
        return invalidDocuments;
    }

    public long getEmptyDocuments() {
        return emptyDocuments;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public long getUniqueWords() {
        return uniqueWords;
    }

    public long getTfRecords() {
        return tfRecords;
    }

    public long getTfidfRecords() {
        return tfidfRecords;
    }

    public long getTfDurationMs() {
        return tfDurationMs;
    }

    public long getIdfDurationMs() {
        return idfDurationMs;
    }

    public long getJoinDurationMs() {
        return joinDurationMs;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public long getPeakHeapUsedBytes() {
        return peakHeapUsedBytes;
    }

    void writeJson(Path path) throws IOException {
        String json = """
                {
                  "inputBytes": %d,
                  "processedDocuments": %d,
                  "invalidDocuments": %d,
                  "emptyDocuments": %d,
                  "totalTokens": %d,
                  "uniqueWords": %d,
                  "tfRecords": %d,
                  "tfidfRecords": %d,
                  "tfDurationMs": %d,
                  "idfDurationMs": %d,
                  "joinDurationMs": %d,
                  "totalDurationMs": %d,
                  "peakHeapUsedBytes": %d
                }
                """.formatted(
                inputBytes,
                processedDocuments,
                invalidDocuments,
                emptyDocuments,
                totalTokens,
                uniqueWords,
                tfRecords,
                tfidfRecords,
                tfDurationMs,
                idfDurationMs,
                joinDurationMs,
                totalDurationMs,
                peakHeapUsedBytes
        );
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }
}
