package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

public final class JoinGroupingComparator extends WritableComparator {

    public JoinGroupingComparator() {
        super(JoinKey.class, true);
    }

    @Override
    public int compare(WritableComparable first, WritableComparable second) {
        JoinKey firstKey = (JoinKey) first;
        JoinKey secondKey = (JoinKey) second;
        return firstKey.getWord().compareTo(secondKey.getWord());
    }
}
