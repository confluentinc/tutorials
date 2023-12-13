package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.*;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;


public class VersionedKTableExampleTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @Test
    public void versionedKTableTest() throws IOException {
        final VersionedKTableExample instance = new VersionedKTableExample();
        final Properties allProps = instance.loadEnvProperties(TEST_CONFIG_FILE);

        final String streamInputTopicName = allProps.getProperty("stream.topic.name");
        final String tableInputTopicName = allProps.getProperty("table.topic.name");
        final String totalResultOutputTopicName = allProps.getProperty("output.topic.name");

        final Topology topology = instance.buildTopology(allProps);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, allProps);
             final Serde<String> stringSerde = Serdes.String()) {
            final Serializer<String> stringSerializer = stringSerde.serializer();
            final Deserializer<String> keyDeserializer = stringSerde.deserializer();

            final TestInputTopic<String, String> streamInputTopic = testDriver.createInputTopic(streamInputTopicName, stringSerializer, stringSerializer);
            final TestInputTopic<String, String> tableInputTopic = testDriver.createInputTopic(tableInputTopicName, stringSerializer, stringSerializer);

            final TestOutputTopic<String, String> outputTopic = testDriver.createOutputTopic(totalResultOutputTopicName, keyDeserializer, stringSerde.deserializer());

            Instant now = Instant.now();

            List<KeyValue<String, String>> streamMessages = Arrays.asList(
                    KeyValue.pair("one", "peanut butter and"),
                    KeyValue.pair("two", "ham and"),
                    KeyValue.pair("three", "cheese and"),
                    KeyValue.pair("four", "tea and"),
                    KeyValue.pair("five", "coffee with")
            );

            List<Long> timestamps = Arrays.asList(
                    now.minus(50, ChronoUnit.SECONDS).toEpochMilli(),
                    now.minus(40, ChronoUnit.SECONDS).toEpochMilli(),
                    now.minus(30, ChronoUnit.SECONDS).toEpochMilli(),
                    now.minus(20, ChronoUnit.SECONDS).toEpochMilli(),
                    now.minus(10, ChronoUnit.SECONDS).toEpochMilli()
            );

            List<KeyValue<String, String>> tableMessagesOriginal = Arrays.asList(
                    KeyValue.pair("one", "jelly"),
                    KeyValue.pair("two", "cheese"),
                    KeyValue.pair("three", "crackers"),
                    KeyValue.pair("four", "biscuits"),
                    KeyValue.pair("five", "cream"));

            List<KeyValue<String, String>> tableMessagesLater = Arrays.asList(
                    KeyValue.pair("one", "sardines"),
                    KeyValue.pair("two", "an old tire"),
                    KeyValue.pair("three", "fish eyes"),
                    KeyValue.pair("four", "moldy bread"),
                    KeyValue.pair("five", "lots of salt"));

            List<Long> forwardTimestamps = Arrays.asList(
                    now.plus(50, ChronoUnit.SECONDS).toEpochMilli(),
                    now.plus(40, ChronoUnit.SECONDS).toEpochMilli(),
                    now.plus(30, ChronoUnit.SECONDS).toEpochMilli(),
                    now.plus(30, ChronoUnit.SECONDS).toEpochMilli(),
                    now.plus(30, ChronoUnit.SECONDS).toEpochMilli()
            );
            sendEvents(tableInputTopic, tableMessagesOriginal, timestamps);
            sendEvents(tableInputTopic, tableMessagesLater, forwardTimestamps);
            sendEvents(streamInputTopic, streamMessages, timestamps);

            final List<KeyValue<String, String>> actualEvents = outputTopic.readKeyValuesToList();
            final List<KeyValue<String, String>> expectedEvents = Arrays.asList(
                    KeyValue.pair("one", "peanut butter and jelly"),
                    KeyValue.pair("two", "ham and cheese"),
                    KeyValue.pair("three", "cheese and crackers"),
                    KeyValue.pair("four", "tea and biscuits"),
                    KeyValue.pair("five", "coffee with cream")
            );

            assertEquals(expectedEvents, actualEvents);
        }
    }

    private void sendEvents(final TestInputTopic<String, String> topic,
                            final List<KeyValue<String, String>> input,
                            final List<Long> timestamps) {
        for (int i = 0; i < input.size(); i++) {
            final long timestamp = timestamps.get(i);
            final String key = input.get(i).key;
            final String value = input.get(i).value;
            topic.pipeInput(key, value, timestamp);
        }
    }
}
