package io.confluent.developer;

import io.confluent.developer.avro.TemperatureReading;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class TemperatureReadingTimestampExtractor implements TimestampExtractor {
    @Override
    public long extract(ConsumerRecord<Object, Object> record, long previousTimestamp) {
        return ((TemperatureReading)record.value()).getTimestamp();
    }
}
