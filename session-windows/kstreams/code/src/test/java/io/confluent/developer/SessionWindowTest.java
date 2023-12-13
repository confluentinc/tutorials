package io.confluent.developer;


import io.confluent.developer.avro.Clicks;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;


public class SessionWindowTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @Test
    public void sessionWindowTest() throws IOException {
        final SessionWindow instance = new SessionWindow();
        final Properties allProps = instance.loadEnvProperties(TEST_CONFIG_FILE);

        final String sessionDataInputTopic = allProps.getProperty("input.topic.name");
        final String outputTopicName = allProps.getProperty("output.topic.name");

        final Topology topology = instance.buildTopology(allProps);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, allProps)) {

            final SpecificAvroSerde<Clicks> exampleAvroSerde = SessionWindow.getSpecificAvroSerde(allProps);

            final Serializer<String> keySerializer = Serdes.String().serializer();
            final Serializer<Clicks> exampleSerializer = exampleAvroSerde.serializer();
            final Deserializer<String> valueDeserializer = Serdes.String().deserializer();
            final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

            final TestInputTopic<String, Clicks>  inputTopic = testDriver.createInputTopic(sessionDataInputTopic,
                                                                                              keySerializer,
                                                                                              exampleSerializer);

            final TestOutputTopic<String, String> outputTopic = testDriver.createOutputTopic(outputTopicName, keyDeserializer, valueDeserializer);
            final String key = "51.56.119.117";
            final List<Clicks> sessionClicks = new ArrayList<>();
            Instant instant = Instant.now();
            final int expectedNumberOfSessions = 2;
            sessionClicks.add(Clicks.newBuilder().setIp(key).setUrl("/etiam/justo/etiam/pretium/iaculis.xml").setTimestamp(instant.toEpochMilli()).build());
            Instant newSessionInstant = instant.plus(6,ChronoUnit.MINUTES);
            sessionClicks.add(Clicks.newBuilder().setIp(key).setUrl("/mauris/morbi/non.jpg").setTimestamp(newSessionInstant.toEpochMilli()).build());
            List<KeyValue<String, Clicks>> keyValues = sessionClicks.stream().map(o -> KeyValue.pair(o.getIp(),o)).collect(Collectors.toList());
            inputTopic.pipeKeyValueList(keyValues);
            
            final List<KeyValue<String, String>> actualResults = outputTopic.readKeyValuesToList();
            // Should result in two sessions
            assertEquals(expectedNumberOfSessions, actualResults.size());
        }
    }
}
