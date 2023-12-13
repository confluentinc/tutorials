package io.confluent.developer;

import io.confluent.developer.avro.Clicks;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class ClickEventTimestampExtractor implements TimestampExtractor {
    @Override
    public long extract(ConsumerRecord<Object, Object> record, long previousTimestamp) {
        return ((Clicks)record.value()).getTimestamp();
    }
}
