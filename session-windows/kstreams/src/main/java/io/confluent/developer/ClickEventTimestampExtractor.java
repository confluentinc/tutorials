package io.confluent.developer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class ClickEventTimestampExtractor implements TimestampExtractor {
    @Override
    public long extract(ConsumerRecord<Object, Object> record, long previousTimestamp) {
        return ((Click)record.value()).timestamp();
    }
}
