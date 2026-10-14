package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public final class TfidfJoinJob {

    private TfidfJoinJob() {
    }

    public static Job create(
            Configuration configuration,
            Path tfInput,
            Path idfInput,
            Path output
    ) throws Exception {

        Job job = Job.getInstance(configuration, "TF-IDF Reduce-side Join");

        job.setJarByClass(TfidfJoinJob.class);

        MultipleInputs.addInputPath(
                job,
                tfInput,
                TextInputFormat.class,
                TfJoinMapper.class
        );

        MultipleInputs.addInputPath(
                job,
                idfInput,
                TextInputFormat.class,
                IdfJoinMapper.class
        );

        job.setReducerClass(TfidfJoinReducer.class);
        job.setPartitionerClass(JoinPartitioner.class);
        job.setGroupingComparatorClass(JoinGroupingComparator.class);

        job.setMapOutputKeyClass(JoinKey.class);
        job.setMapOutputValueClass(Text.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);

        job.setNumReduceTasks(3);

        FileOutputFormat.setOutputPath(job, output);

        return job;
    }
}
