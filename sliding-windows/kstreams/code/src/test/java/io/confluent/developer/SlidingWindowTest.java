package io.confluent.developer;


import io.confluent.common.utils.TestUtils;
import io.confluent.developer.avro.TemperatureReading;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;


public class SlidingWindowTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @Test
    public void slidingWindowTest() throws IOException {
        final SlidingWindow instance = new SlidingWindow();
        final Properties envProps = instance.loadEnvProperties(TEST_CONFIG_FILE);

        final Properties streamProps = instance.buildStreamsProperties(envProps);
        final String temperatureReadingsInputTopic = envProps.getProperty("input.topic.name");
        final String outputTopicName = envProps.getProperty("output.topic.name");

        final Topology topology = instance.buildTopology(envProps);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps)) {

            final SpecificAvroSerde<TemperatureReading> exampleAvroSerde = SlidingWindow.getSpecificAvroSerde(envProps);

            final Serializer<String> keySerializer = Serdes.String().serializer();
            final Serializer<TemperatureReading> exampleSerializer = exampleAvroSerde.serializer();
            final Deserializer<Double> valueDeserializer = Serdes.Double().deserializer();
            final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

            final TestInputTopic<String, TemperatureReading>  inputTopic = testDriver.createInputTopic(temperatureReadingsInputTopic,
                                                                                              keySerializer,
                                                                                              exampleSerializer);

            final TestOutputTopic<String, Double> outputTopic = testDriver.createOutputTopic(outputTopicName, keyDeserializer, valueDeserializer);
            final String key = "device-1";
            final List<TemperatureReading> temperatureReadings = new ArrayList<>();
            Instant instant = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            temperatureReadings.add(TemperatureReading.newBuilder().setDeviceId(key).setTemp(80.0).setTimestamp(instant.getEpochSecond()).build());
            temperatureReadings.add(TemperatureReading.newBuilder().setDeviceId(key).setTemp(90.0).setTimestamp(instant.plusMillis(200).getEpochSecond()).build());
            temperatureReadings.add(TemperatureReading.newBuilder().setDeviceId(key).setTemp(95.0).setTimestamp(instant.plusMillis(400).getEpochSecond()).build());
            temperatureReadings.add(TemperatureReading.newBuilder().setDeviceId(key).setTemp(100.0).setTimestamp(instant.plusMillis(500).getEpochSecond()).build());

            List<KeyValue<String, TemperatureReading>> keyValues = temperatureReadings.stream().map(o -> KeyValue.pair(o.getDeviceId(),o)).collect(Collectors.toList());
            inputTopic.pipeKeyValueList(keyValues);
            final List<KeyValue<String, Double>> expectedValues = Arrays.asList(KeyValue.pair(key, 80.0), KeyValue.pair(key, 85.0), KeyValue.pair(key, 88.33), KeyValue.pair(key, 91.25));

            final List<KeyValue<String, Double>> actualResults = outputTopic.readKeyValuesToList();
            assertEquals(expectedValues, actualResults);
        }
    }
}
