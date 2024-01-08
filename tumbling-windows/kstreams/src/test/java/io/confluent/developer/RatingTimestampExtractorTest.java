package io.confluent.developer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RatingTimestampExtractorTest {

    @Test
    public void testTimestampExtraction() {
        RatingTimestampExtractor rte = new RatingTimestampExtractor();

        MovieRating treeOfLife = new MovieRating("Tree of Life", 2011, 9.9, "2019-04-25T18:00:00-0700");
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("ratings", 0, 1, "Tree of Life", treeOfLife);

        long timestamp = rte.extract(record, 0);

        assertEquals(1556240400000L, timestamp);
    }
}
