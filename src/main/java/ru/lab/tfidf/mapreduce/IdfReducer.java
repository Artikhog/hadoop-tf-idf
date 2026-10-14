package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class IdfReducer
        extends Reducer<Text, IntWritable, Text, DoubleWritable> {

    private long totalDocuments;

    private final DoubleWritable outputValue = new DoubleWritable();

    @Override
    protected void setup(Context context) {
        totalDocuments = context.getConfiguration()
                .getLong("tfidf.total.documents", 0);

        if (totalDocuments <= 0) {
            throw new IllegalStateException(
                    "Total documents must be greater than zero"
            );
        }
    }

    @Override
    protected void reduce(
            Text key,
            Iterable<IntWritable> values,
            Context context
    ) throws IOException, InterruptedException {

        long documentFrequency = 0;

        for (IntWritable value : values) {
            documentFrequency += value.get();
        }

        double idf = Math.log(
                (double) totalDocuments / documentFrequency
        );

        outputValue.set(idf);

        context.write(key, outputValue);
    }
}