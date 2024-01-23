package io.confluent.developer;


import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.confluent.developer.SlidingWindow.INPUT_TOPIC;
import static io.confluent.developer.SlidingWindow.OUTPUT_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class SlidingWindowTest {
    final SlidingWindow slidingWindow = new SlidingWindow();

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @Test
    public void testSlidingWindows() {
        final SlidingWindow instance = new SlidingWindow();
        final Properties props = new Properties();

        try (TopologyTestDriver testDriver = new TopologyTestDriver(slidingWindow.buildTopology(props));
             Serde<TemperatureReading> tempReadingSerde = StreamsSerde.serdeFor(TemperatureReading.class);
             Serde<TempAverage> tempAverageSerde = StreamsSerde.serdeFor(TempAverage.class)) {

            final Serializer<String> keySerializer = Serdes.String().serializer();
            final Serializer<TemperatureReading> exampleSerializer = tempReadingSerde.serializer();
            final Deserializer<Double> valueDeserializer = Serdes.Double().deserializer();
            final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

            final TestInputTopic<String, TemperatureReading>  inputTopic = testDriver.createInputTopic(INPUT_TOPIC,
                    keySerializer,
                    exampleSerializer);

            final TestOutputTopic<String, Double> outputTopic = testDriver.createOutputTopic(OUTPUT_TOPIC, keyDeserializer, valueDeserializer);
            final String key = "device-1";
            final List<TemperatureReading> temperatureReadings = new ArrayList<>();
            Instant instant = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            temperatureReadings.add(new TemperatureReading(80.0, instant.getEpochSecond(), key));
            temperatureReadings.add(new TemperatureReading(90.0, instant.plusMillis(200).getEpochSecond(), key));
            temperatureReadings.add(new TemperatureReading(95.0, instant.plusMillis(400).getEpochSecond(), key));
            temperatureReadings.add(new TemperatureReading(100.0, instant.plusMillis(500).getEpochSecond(), key));

            List<KeyValue<String, TemperatureReading>> keyValues = temperatureReadings.stream().map(o -> KeyValue.pair(o.device_id(),o)).collect(Collectors.toList());
            inputTopic.pipeKeyValueList(keyValues);
            final List<KeyValue<String, Double>> expectedValues = Arrays.asList(KeyValue.pair(key, 80.0), KeyValue.pair(key, 85.0), KeyValue.pair(key, 88.33), KeyValue.pair(key, 91.25));

            final List<KeyValue<String, Double>> actualResults = outputTopic.readKeyValuesToList();
            assertEquals(expectedValues, actualResults);
        }
    }
}
