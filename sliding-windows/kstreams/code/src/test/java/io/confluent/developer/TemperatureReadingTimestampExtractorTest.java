package io.confluent.developer;

import io.confluent.developer.avro.TemperatureReading;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TemperatureReadingTimestampExtractorTest {

    @Test
    public void testTimestampExtraction() {
        TemperatureReadingTimestampExtractor rte = new TemperatureReadingTimestampExtractor();

        TemperatureReading tempReading = TemperatureReading.newBuilder().setTemp(98.6).setTimestamp(5L).setDeviceId("device_1").build();
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("xxx", 0, 1, "device_1", tempReading);

        long timestamp = rte.extract(record, 0);

        assertEquals(5L, timestamp);
    }
}
