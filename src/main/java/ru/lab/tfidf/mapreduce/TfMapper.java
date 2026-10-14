package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import ru.lab.tfidf.common.Tokenizer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TfMapper
        extends Mapper<LongWritable, Text, Text, DoubleWritable> {

    private final Text outputKey = new Text();
    private final DoubleWritable outputValue = new DoubleWritable();

    @Override
    protected void map(
            LongWritable key,
            Text value,
            Context context
    ) throws IOException, InterruptedException {

        String line = value.toString();

        int separator = line.indexOf('\t');

        if (separator <= 0 || separator == line.length() - 1) {
            context.getCounter("TF", "INVALID_DOCUMENTS").increment(1);
            return;
        }

        String documentId = line.substring(0, separator);
        String documentText = line.substring(separator + 1);

        List<String> tokens = Tokenizer.tokenize(documentText);

        if (tokens.isEmpty()) {
            context.getCounter("TF", "EMPTY_DOCUMENTS").increment(1);
            return;
        }

        Map<String, Integer> frequencies = new HashMap<>();

        for (String token : tokens) {
            frequencies.merge(token, 1, Integer::sum);
        }

        int totalWords = tokens.size();

        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            String word = entry.getKey();
            int count = entry.getValue();

            double tf = (double) count / totalWords;

            outputKey.set(documentId + ":" + word);
            outputValue.set(tf);

            context.write(outputKey, outputValue);
        }

        context.getCounter("TF", "PROCESSED_DOCUMENTS").increment(1);
        context.getCounter("TF", "TOTAL_TOKENS").increment(totalWords);
    }
}