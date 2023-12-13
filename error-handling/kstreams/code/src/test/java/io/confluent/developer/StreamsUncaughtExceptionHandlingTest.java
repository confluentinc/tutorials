package io.confluent.developer;


import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertThrows;


public class StreamsUncaughtExceptionHandlingTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;
    private TopologyTestDriver testDriver;


    @Before
    public void setUp() throws IOException {
        final StreamsUncaughtExceptionHandling instance = new StreamsUncaughtExceptionHandling();
        final Properties allProps = instance.loadEnvProperties(TEST_CONFIG_FILE);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        final String sessionDataInputTopic = allProps.getProperty("input.topic.name");
        final String outputTopicName = allProps.getProperty("output.topic.name");

        final Topology topology = instance.buildTopology(allProps);
        testDriver = new TopologyTestDriver(topology, allProps);
        final Serializer<String> keySerializer = Serdes.String().serializer();
        final Serializer<String> exampleSerializer = Serdes.String().serializer();
        final Deserializer<String> valueDeserializer = Serdes.String().deserializer();
        final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

        inputTopic = testDriver.createInputTopic(sessionDataInputTopic, keySerializer, exampleSerializer);
        outputTopic = testDriver.createOutputTopic(outputTopicName, keyDeserializer, valueDeserializer);
    }

    @After
    public void tearDown() {
        testDriver.close();
    }

    @Test
    public void shouldThrowException() {
        assertThrows(org.apache.kafka.streams.errors.StreamsException.class, () -> inputTopic.pipeValueList(Arrays.asList("foo", "bar")));
    }

    @Test
    public void shouldProcessValues() {
        List<String> validMessages =  Collections.singletonList("foo");
        List<String> expectedMessages = validMessages.stream().map(String::toUpperCase).collect(Collectors.toList());
        inputTopic.pipeValueList(validMessages);
        List<String> actualResults = outputTopic.readValuesToList();
        assertEquals(expectedMessages, actualResults);
    }

}
