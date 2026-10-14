package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.Iterator;

public class TfidfJoinReducer
        extends Reducer<JoinKey, Text, Text, DoubleWritable> {

    private final Text outputKey = new Text();
    private final DoubleWritable outputValue = new DoubleWritable();

    @Override
    protected void reduce(
            JoinKey key,
            Iterable<Text> values,
            Context context
    ) throws IOException, InterruptedException {

        String word = key.getWord().toString();
        Iterator<Text> iterator = values.iterator();

        if (key.getRecordType() != JoinKey.IDF || !iterator.hasNext()) {
            context.getCounter("JOIN", "MISSING_IDF").increment(1);
            return;
        }

        double idf;
        try {
            idf = Double.parseDouble(iterator.next().toString());
            if (!Double.isFinite(idf)) {
                throw new NumberFormatException("Non-finite IDF");
            }
        } catch (NumberFormatException exception) {
            context.getCounter("JOIN", "INVALID_IDF_VALUES").increment(1);
            return;
        }

        while (iterator.hasNext()) {
            String record = iterator.next().toString();
            int separator = record.indexOf('\t');

            if (separator <= 0 || separator == record.length() - 1) {
                context.getCounter("JOIN", "INVALID_TF_RECORDS").increment(1);
                continue;
            }

            String documentId = record.substring(0, separator);
            double tf;

            try {
                tf = Double.parseDouble(record.substring(separator + 1));
                if (!Double.isFinite(tf)) {
                    throw new NumberFormatException("Non-finite TF");
                }
            } catch (NumberFormatException exception) {
                context.getCounter("JOIN", "INVALID_TF_VALUES").increment(1);
                continue;
            }

            outputKey.set(documentId + ":" + word);
            outputValue.set(tf * idf);

            context.write(outputKey, outputValue);
        }
    }
}
