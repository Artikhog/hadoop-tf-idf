package ru.lab.tfidf.local;

import java.nio.file.Path;

public final class LocalTfidfMain {

    private LocalTfidfMain() {
    }

    public static void main(String[] args) {
        try {
            Arguments arguments = Arguments.parse(args);
            LocalTfidfStatistics statistics = new LocalTfidfProcessor().process(
                    arguments.input(),
                    arguments.output(),
                    arguments.overwrite()
            );
            System.out.printf(
                    "Local TF-IDF completed: %d documents, %d unique words, %d ms%n",
                    statistics.getProcessedDocuments(),
                    statistics.getUniqueWords(),
                    statistics.getTotalDurationMs()
            );
        } catch (Exception exception) {
            System.err.println("Local TF-IDF failed: " + exception.getMessage());
            System.err.println(
                    "Usage: LocalTfidfMain --input <file-or-directory> "
                            + "--output <directory> [--overwrite]"
            );
            System.exit(1);
        }
    }

    private record Arguments(Path input, Path output, boolean overwrite) {

        private static Arguments parse(String[] args) {
            Path input = null;
            Path output = null;
            boolean overwrite = false;

            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--input" -> {
                        input = pathValue(args, ++index, "--input");
                    }
                    case "--output" -> {
                        output = pathValue(args, ++index, "--output");
                    }
                    case "--overwrite" -> overwrite = true;
                    default -> throw new IllegalArgumentException(
                            "Unknown argument: " + args[index]);
                }
            }

            if (input == null || output == null) {
                throw new IllegalArgumentException("--input and --output are required");
            }
            return new Arguments(input, output, overwrite);
        }

        private static Path pathValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return Path.of(args[index]);
        }
    }
}
