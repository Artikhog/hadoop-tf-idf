package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public final class IdfJob {

    private IdfJob() {
    }

    public static Job create(
            Configuration configuration,
            Path input,
            Path output,
            long totalDocuments
    ) throws Exception {

        configuration.setLong(
                "tfidf.total.documents",
                totalDocuments
        );

        Job job = Job.getInstance(
                configuration,
                "IDF Calculation"
        );

        job.setJarByClass(IdfJob.class);

        job.setMapperClass(IdfMapper.class);
        job.setCombinerClass(IdfCombiner.class);
        job.setReducerClass(IdfReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);

        job.setNumReduceTasks(3);

        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);

        return job;
    }
}