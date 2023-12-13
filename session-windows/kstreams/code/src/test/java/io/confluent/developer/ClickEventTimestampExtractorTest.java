package io.confluent.developer;

import io.confluent.developer.avro.Clicks;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.junit.Test;

import static org.junit.Assert.*;

public class ClickEventTimestampExtractorTest {

    @Test
    public void testExtract() {

        TimestampExtractor timestampExtractor = new ClickEventTimestampExtractor();
        long ts =  3333333L;
        Clicks clickEvent = Clicks.newBuilder().setTimestamp(ts).setIp("ip").setUrl("url").build();
        ConsumerRecord<Object, Object> consumerRecord = new ConsumerRecord<>("topic", 1, 1L, clickEvent.getIp(), clickEvent);
        long extracted = timestampExtractor.extract(consumerRecord, -1L);
        assertEquals(ts, extracted);
    }
}