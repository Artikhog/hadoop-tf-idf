package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;

public final class JoinPartitioner extends Partitioner<JoinKey, Text> {

    @Override
    public int getPartition(JoinKey key, Text value, int numPartitions) {
        return (key.getWord().hashCode() & Integer.MAX_VALUE) % numPartitions;
    }
}
