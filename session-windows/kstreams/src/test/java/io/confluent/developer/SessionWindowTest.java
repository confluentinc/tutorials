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
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.confluent.developer.SessionWindow.INPUT_TOPIC;
import static io.confluent.developer.SessionWindow.OUTPUT_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class SessionWindowTest {

    private final SessionWindow sessionWindow = new SessionWindow();


    @Test
    public void sessionWindowTest() {
        Properties properties = new Properties();

        try (TopologyTestDriver testDriver = new TopologyTestDriver(sessionWindow.buildTopology(properties));
             Serde<Click> clickSerde = StreamsSerde.serdeFor(Click.class)) {

            final Serializer<String> keySerializer = Serdes.String().serializer();
            final Serializer<Click> exampleSerializer = clickSerde.serializer();
            final Deserializer<String> valueDeserializer = Serdes.String().deserializer();
            final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

            final TestInputTopic<String, Click>  inputTopic = testDriver.createInputTopic(INPUT_TOPIC,
                    keySerializer,
                    exampleSerializer);

            final TestOutputTopic<String, String> outputTopic = testDriver.createOutputTopic(OUTPUT_TOPIC, keyDeserializer, valueDeserializer);
            final String key = "51.56.119.117";
            final List<Click> sessionClicks = new ArrayList<>();
            Instant instant = Instant.now();
            final int expectedNumberOfSessions = 2;
            sessionClicks.add(new Click(key, instant.toEpochMilli(), "/etiam/justo/etiam/pretium/iaculis.xml"));
            Instant newSessionInstant = instant.plus(6,ChronoUnit.MINUTES);
            sessionClicks.add(new Click(key, newSessionInstant.toEpochMilli(), "/mauris/morbi/non.jpg"));
            List<KeyValue<String, Click>> keyValues = sessionClicks.stream().map(o -> KeyValue.pair(o.ip(),o)).collect(Collectors.toList());
            inputTopic.pipeKeyValueList(keyValues);

            final List<KeyValue<String, String>> actualResults = outputTopic.readKeyValuesToList();
            // Should result in two sessions
            assertEquals(expectedNumberOfSessions, actualResults.size());
        }
    }
}
