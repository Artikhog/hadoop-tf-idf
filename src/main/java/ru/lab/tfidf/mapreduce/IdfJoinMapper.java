package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class IdfJoinMapper
        extends Mapper<LongWritable, Text, JoinKey, Text> {

    private final JoinKey outputKey = new JoinKey();
    private final Text outputValue = new Text();

    @Override
    protected void map(
            LongWritable key,
            Text value,
            Context context
    ) throws IOException, InterruptedException {

        String line = value.toString();

        int separator = line.indexOf('\t');

        if (separator <= 0 || separator == line.length() - 1) {
            context.getCounter("JOIN", "INVALID_IDF_RECORDS").increment(1);
            return;
        }

        String word = line.substring(0, separator);
        String idf = line.substring(separator + 1);

        outputKey.set(word, JoinKey.IDF);
        outputValue.set(idf);

        context.write(outputKey, outputValue);
    }
}
