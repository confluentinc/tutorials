package io.confluent.developer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class TemperatureReadingTimestampExtractorTest {

    @Test
    public void testTimestampExtraction() {
        TemperatureReadingTimestampExtractor rte = new TemperatureReadingTimestampExtractor();

        long timestamp = 1705934070L;
        TemperatureReading tempReading = new TemperatureReading(98.6, timestamp, "device_1");
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("xxx", 0, 1, "device_1", tempReading);

        assertEquals(timestamp, rte.extract(record, 0));
    }
}
