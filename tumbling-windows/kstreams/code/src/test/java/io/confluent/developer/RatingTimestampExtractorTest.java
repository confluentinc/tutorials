package io.confluent.developer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;

import io.confluent.developer.avro.Rating;

import static org.junit.Assert.assertEquals;

public class RatingTimestampExtractorTest {

    @Test
    public void testTimestampExtraction() {
        RatingTimestampExtractor rte = new RatingTimestampExtractor();

        Rating treeOfLife = Rating.newBuilder().setTitle("Tree of Life").setReleaseYear(2011).setRating(9.9).setTimestamp("2019-04-25T18:00:00-0700").build();
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("ratings", 0, 1, "Tree of Life", treeOfLife);

        long timestamp = rte.extract(record, 0);

        assertEquals(1556240400000L, timestamp);
    }
}
