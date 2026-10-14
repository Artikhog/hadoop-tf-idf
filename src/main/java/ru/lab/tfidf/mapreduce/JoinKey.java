package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class JoinKey implements WritableComparable<JoinKey> {

    public static final byte IDF = 0;
    public static final byte TF = 1;

    private final Text word = new Text();
    private byte recordType;

    public JoinKey() {
    }

    public void set(String word, byte recordType) {
        this.word.set(word);
        this.recordType = recordType;
    }

    public Text getWord() {
        return word;
    }

    public byte getRecordType() {
        return recordType;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        word.write(out);
        out.writeByte(recordType);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        word.readFields(in);
        recordType = in.readByte();
    }

    @Override
    public int compareTo(JoinKey other) {
        int wordComparison = word.compareTo(other.word);
        return wordComparison != 0
                ? wordComparison
                : Byte.compare(recordType, other.recordType);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof JoinKey other)) {
            return false;
        }
        return recordType == other.recordType && word.equals(other.word);
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, recordType);
    }
}
