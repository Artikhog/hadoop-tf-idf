package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class TfJoinMapper
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

        if (separator < 0) {
            context.getCounter("JOIN", "INVALID_TF_RECORDS").increment(1);
            return;
        }

        String documentWord = line.substring(0, separator);
        String tf = line.substring(separator + 1);

        int wordSeparator = documentWord.lastIndexOf(':');

        if (wordSeparator <= 0 || wordSeparator == documentWord.length() - 1) {
            context.getCounter("JOIN", "INVALID_TF_RECORDS").increment(1);
            return;
        }

        String documentId = documentWord.substring(0, wordSeparator);
        String word = documentWord.substring(wordSeparator + 1);

        outputKey.set(word, JoinKey.TF);
        outputValue.set(documentId + "\t" + tf);

        context.write(outputKey, outputValue);
    }
}
