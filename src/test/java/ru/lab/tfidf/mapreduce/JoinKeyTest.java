package ru.lab.tfidf.mapreduce;

import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinKeyTest {

    @Test
    void serializesAndDeserializesBothFields() throws IOException {
        JoinKey original = key("hadoop", JoinKey.TF);
        byte[] serialized = serialize(original);

        DataInputBuffer input = new DataInputBuffer();
        input.reset(serialized, serialized.length);
        JoinKey restored = new JoinKey();
        restored.readFields(input);

        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertEquals("hadoop", restored.getWord().toString());
        assertEquals(JoinKey.TF, restored.getRecordType());
    }

    @Test
    void sortsByWordBeforeRecordType() {
        assertTrue(key("hadoop", JoinKey.TF)
                .compareTo(key("spark", JoinKey.IDF)) < 0);
    }

    @Test
    void sortsIdfBeforeTfForTheSameWord() {
        assertTrue(key("hadoop", JoinKey.IDF)
                .compareTo(key("hadoop", JoinKey.TF)) < 0);
    }

    @Test
    void partitionerIgnoresRecordType() {
        JoinPartitioner partitioner = new JoinPartitioner();

        int idfPartition = partitioner.getPartition(
                key("hadoop", JoinKey.IDF), new Text("idf"), 7);
        int tfPartition = partitioner.getPartition(
                key("hadoop", JoinKey.TF), new Text("tf"), 7);

        assertEquals(idfPartition, tfPartition);
    }

    @Test
    void groupingComparatorIgnoresRecordType() throws IOException {
        JoinGroupingComparator comparator = new JoinGroupingComparator();
        byte[] idf = serialize(key("hadoop", JoinKey.IDF));
        byte[] tf = serialize(key("hadoop", JoinKey.TF));
        byte[] otherWord = serialize(key("spark", JoinKey.IDF));

        assertEquals(0, comparator.compare(
                idf, 0, idf.length, tf, 0, tf.length));
        assertTrue(comparator.compare(
                idf, 0, idf.length,
                otherWord, 0, otherWord.length) < 0);
    }

    private static JoinKey key(String word, byte recordType) {
        JoinKey key = new JoinKey();
        key.set(word, recordType);
        return key;
    }

    private static byte[] serialize(JoinKey key) throws IOException {
        DataOutputBuffer output = new DataOutputBuffer();
        key.write(output);
        byte[] serialized = new byte[output.getLength()];
        System.arraycopy(output.getData(), 0, serialized, 0, output.getLength());
        return serialized;
    }
}
