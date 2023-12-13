package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CogroupingStreamsTest {

    @Test
    @DisplayName("CoGrouping Kafka Streams Test")
    void cogroupingTest() {
        final CogroupingStreams instance = new CogroupingStreams();
        final Properties allProps = new Properties();

        final Topology topology = instance.buildTopology(allProps);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, allProps)) {

            final Serde<String> stringAvroSerde = Serdes.String();
            final Serde<LoginEvent> loginEventSerde = StreamsSerde.serdeFor(LoginEvent.class);
            final Serde<LoginRollup> rollupSerde = StreamsSerde.serdeFor(LoginRollup.class);

            final Serializer<String> keySerializer = stringAvroSerde.serializer();
            final Deserializer<String> keyDeserializer = stringAvroSerde.deserializer();
            final Serializer<LoginEvent> loginEventSerializer = loginEventSerde.serializer();


            final TestInputTopic<String, LoginEvent> appOneInputTopic = testDriver.createInputTopic(CogroupingStreams.APP_ONE_INPUT_TOPIC, keySerializer, loginEventSerializer);
            final TestInputTopic<String, LoginEvent>  appTwoInputTopic = testDriver.createInputTopic(CogroupingStreams.APP_TWO_INPUT_TOPIC, keySerializer, loginEventSerializer);
            final TestInputTopic<String, LoginEvent>  appThreeInputTopic = testDriver.createInputTopic(CogroupingStreams.APP_THREE_INPUT_TOPIC, keySerializer, loginEventSerializer);

            final TestOutputTopic<String, LoginRollup> outputTopic = testDriver.createOutputTopic(CogroupingStreams.OUTPUT_TOPIC, keyDeserializer, rollupSerde.deserializer());


            final List<LoginEvent> appOneEvents = new ArrayList<>();
            appOneEvents.add(createLoginEvent("one", "foo", 5L));
            appOneEvents.add(createLoginEvent("one", "bar", 6L));
            appOneEvents.add(createLoginEvent("one", "bar", 7L));
            final List<LoginEvent> appTwoEvents = new ArrayList<>();
            appTwoEvents.add(createLoginEvent("two", "foo", 5L));
            appTwoEvents.add(createLoginEvent("two", "foo", 6L));
            appTwoEvents.add(createLoginEvent("two", "bar", 7L));
            final List<LoginEvent> appThreeEvents = new ArrayList<>();
            appThreeEvents.add(createLoginEvent("three", "foo", 5L));
            appThreeEvents.add(createLoginEvent("three", "foo", 6L));
            appThreeEvents.add(createLoginEvent("three", "bar", 7L));
            appThreeEvents.add(createLoginEvent("three", "bar", 9L));

            final Map<String, Map<String, Long>> expectedEventRollups = new TreeMap<>();
            final Map<String, Long> expectedAppOneRollup = new HashMap<>();
            final LoginRollup expectedLoginRollup = new LoginRollup(expectedEventRollups);
            expectedAppOneRollup.put("foo", 1L);
            expectedAppOneRollup.put("bar", 2L);
            expectedEventRollups.put("one", expectedAppOneRollup);

            final Map<String, Long> expectedAppTwoRollup = new HashMap<>();
            expectedAppTwoRollup.put("foo", 2L);
            expectedAppTwoRollup.put("bar", 1L);
            expectedEventRollups.put("two", expectedAppTwoRollup);

            final Map<String, Long> expectedAppThreeRollup = new HashMap<>();
            expectedAppThreeRollup.put("foo", 2L);
            expectedAppThreeRollup.put("bar", 2L);
            expectedEventRollups.put("three", expectedAppThreeRollup);

            sendEvents(appOneEvents, appOneInputTopic);
            sendEvents(appTwoEvents, appTwoInputTopic);
            sendEvents(appThreeEvents, appThreeInputTopic);

            final List<LoginRollup> actualLoginEventResults = outputTopic.readValuesToList();
            final Map<String, Map<String, Long>> actualRollupMap = new HashMap<>();
            for (LoginRollup actualLoginEventResult : actualLoginEventResults) {
                actualRollupMap.putAll(actualLoginEventResult.loginByAppIdAndUserId());
            }
            final LoginRollup actualLoginRollup = new LoginRollup(actualRollupMap);

            assertEquals(expectedLoginRollup, actualLoginRollup);
        }
    }

    private LoginEvent createLoginEvent(String appId, String userId, long time) {
        return new LoginEvent(appId, userId, time);
    }
    private void sendEvents(List<LoginEvent> events, TestInputTopic<String, LoginEvent> testInputTopic) {
        for (LoginEvent event : events) {
            testInputTopic.pipeInput(event.appId(), event);
        }
    }

}