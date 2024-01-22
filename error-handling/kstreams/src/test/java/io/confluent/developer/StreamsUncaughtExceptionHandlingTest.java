package io.confluent.developer;


import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class StreamsUncaughtExceptionHandlingTest {

    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;
    private TopologyTestDriver testDriver;


    @BeforeEach
    public void setUp() {
        final StreamsUncaughtExceptionHandling instance = new StreamsUncaughtExceptionHandling();
        final Properties properties = new Properties();
        final String sessionDataInputTopic = StreamsUncaughtExceptionHandling.INPUT_TOPIC;
        final String outputTopicName = StreamsUncaughtExceptionHandling.OUTPUT_TOPIC;

        final Topology topology = instance.buildTopology(properties);
        testDriver = new TopologyTestDriver(topology, properties);
        final Serializer<String> keySerializer = Serdes.String().serializer();
        final Serializer<String> exampleSerializer = Serdes.String().serializer();
        final Deserializer<String> valueDeserializer = Serdes.String().deserializer();
        final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

        inputTopic = testDriver.createInputTopic(sessionDataInputTopic, keySerializer, exampleSerializer);
        outputTopic = testDriver.createOutputTopic(outputTopicName, keyDeserializer, valueDeserializer);
    }

    @AfterEach
    public void tearDown() {
        testDriver.close();
    }

    @Test
    void shouldThrowException() {
        assertThrows(org.apache.kafka.streams.errors.StreamsException.class, () -> inputTopic.pipeValueList(Arrays.asList("foo", "bar")));
    }

    @Test
    void shouldProcessValues() {
        List<String> validMessages =  Collections.singletonList("foo");
        List<String> expectedMessages = validMessages.stream().map(String::toUpperCase).collect(Collectors.toList());
        inputTopic.pipeValueList(validMessages);
        List<String> actualResults = outputTopic.readValuesToList();
        assertEquals(expectedMessages, actualResults);
    }

}
