package ru.lab.tfidf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;

import ru.lab.tfidf.mapreduce.TfJob;
import ru.lab.tfidf.mapreduce.IdfJob;
import ru.lab.tfidf.mapreduce.TfidfJoinJob;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length != 4) {
            System.err.println(
                    "Usage: hadoop jar tfidf.jar " +
                            "<input> <tf-output> <idf-output> <tfidf-output>"
            );
            System.exit(1);
        }

        Configuration configuration = new Configuration();

        Path input = new Path(args[0]);
        Path tfOutput = new Path(args[1]);
        Path idfOutput = new Path(args[2]);
        Path tfidfOutput = new Path(args[3]);

        Job tfJob = TfJob.create(
                configuration,
                input,
                tfOutput
        );

        if (!tfJob.waitForCompletion(true)) {
            System.exit(1);
        }

        long totalDocuments = tfJob
                .getCounters()
                .findCounter("TF", "PROCESSED_DOCUMENTS")
                .getValue();

        System.out.println("Total documents: " + totalDocuments);

        Job idfJob = IdfJob.create(
                configuration,
                tfOutput,
                idfOutput,
                totalDocuments
        );

        if (!idfJob.waitForCompletion(true)) {
            System.exit(1);
        }

        Job joinJob = TfidfJoinJob.create(
                configuration,
                new Path(tfOutput, "part-m-*"),
                new Path(idfOutput, "part-r-*"),
                tfidfOutput
        );

        if (!joinJob.waitForCompletion(true)) {
            System.exit(1);
        }

        System.out.println("TF-IDF calculation completed");
    }
}