package io.confluent.developer;


import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertSame;


class KafkaStreamsPunctuationTest {

@Test
    void punctuationTest() {
        final KafkaStreamsPunctuation instance = new KafkaStreamsPunctuation();
        final Properties envProps = new Properties();

        final String pageViewsInputTopic = KafkaStreamsPunctuation.INPUT_TOPIC;
        final String outputTopicName = KafkaStreamsPunctuation.OUTPUT_TOPIC;

        final Topology topology = instance.buildTopology(envProps);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, envProps)) {

            final Serde<LoginTime> loginTimeSerde = StreamsSerde.serdeFor(LoginTime.class);

            final Serializer<String> keySerializer = Serdes.String().serializer();
            final Serializer<LoginTime> exampleSerializer = loginTimeSerde.serializer();
            final Deserializer<Long> valueDeserializer = Serdes.Long().deserializer();
            final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

            final TestInputTopic<String, LoginTime>  inputTopic = testDriver.createInputTopic(pageViewsInputTopic,
                                                                                              keySerializer,
                                                                                              exampleSerializer);

            final TestOutputTopic<String, Long> outputTopic = testDriver.createOutputTopic(outputTopicName, keyDeserializer, valueDeserializer);

            final List<LoginTime> loggedOnTimes = new ArrayList<>();
            loggedOnTimes.add(new LoginTime(5L, "user-1", "test-page"));
            loggedOnTimes.add(new LoginTime(5L, "user-2", "test-page"));
            loggedOnTimes.add(new LoginTime(10L, "user-1", "test-page"));
            loggedOnTimes.add(new LoginTime(25L, "user-3", "test-page"));
            loggedOnTimes.add(new LoginTime(10L, "user-2", "test-page"));

            List<KeyValue<String, LoginTime>> keyValues = loggedOnTimes.stream().map(o -> KeyValue.pair(o.userId(),o)).collect(Collectors.toList());
            inputTopic.pipeKeyValueList(keyValues, Instant.now(), Duration.ofSeconds(2));

            final List<KeyValue<String, Long>> actualResults = outputTopic.readKeyValuesToList();
            assertThat(actualResults.size(), is(greaterThanOrEqualTo(1)));

            KeyValueStore<String, Long> store = testDriver.getKeyValueStore("logintime-store");

            testDriver.advanceWallClockTime(Duration.ofSeconds(20));
            
            assertSame(store.get("user-1"), 0L);
            assertSame(store.get("user-2"), 0L);
            assertSame(store.get("user-3"), 0L);
        }
    }
}
