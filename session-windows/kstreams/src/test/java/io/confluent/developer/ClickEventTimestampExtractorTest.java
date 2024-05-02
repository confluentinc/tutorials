package io.confluent.developer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClickEventTimestampExtractorTest {

    @Test
    public void testExtract() {

        TimestampExtractor timestampExtractor = new ClickEventTimestampExtractor();
        long ts =  3333333L;
        Click clickEvent = new Click("ip", ts, "url");
        ConsumerRecord<Object, Object> consumerRecord = new ConsumerRecord<>("topic", 1, 1L, clickEvent.ip(), clickEvent);
        long extracted = timestampExtractor.extract(consumerRecord, -1L);
        assertEquals(ts, extracted);
    }
}