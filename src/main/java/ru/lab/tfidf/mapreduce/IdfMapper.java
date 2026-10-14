package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class IdfMapper
        extends Mapper<LongWritable, Text, Text, IntWritable> {

    private static final IntWritable ONE = new IntWritable(1);

    private final Text outputKey = new Text();

    @Override
    protected void map(
            LongWritable key,
            Text value,
            Context context
    ) throws IOException, InterruptedException {

        String line = value.toString();

        int separator = line.indexOf('\t');

        if (separator < 0) {
            context.getCounter("IDF", "INVALID_RECORDS").increment(1);
            return;
        }

        String documentWord = line.substring(0, separator);

        int wordSeparator = documentWord.lastIndexOf(':');

        if (wordSeparator < 0) {
            context.getCounter("IDF", "INVALID_RECORDS").increment(1);
            return;
        }

        String word = documentWord.substring(wordSeparator + 1);

        outputKey.set(word);

        context.write(outputKey, ONE);
    }
}
