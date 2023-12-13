package io.confluent.developer;


import io.confluent.developer.avro.LoginTime;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertSame;



public class KafkaStreamsPunctuationTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @Test
    public void punctuationTest() throws IOException {
        final KafkaStreamsPunctuation instance = new KafkaStreamsPunctuation();
        final Properties envProps = instance.loadEnvProperties(TEST_CONFIG_FILE);

        final Properties streamProps = instance.buildStreamsProperties(envProps);
        final String pageviewsInputTopic = envProps.getProperty("input.topic.name");
        final String outputTopicName = envProps.getProperty("output.topic.name");

        final Topology topology = instance.buildTopology(envProps);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps)) {

            final SpecificAvroSerde<LoginTime> exampleAvroSerde = KafkaStreamsPunctuation.getSpecificAvroSerde(envProps);

            final Serializer<String> keySerializer = Serdes.String().serializer();
            final Serializer<LoginTime> exampleSerializer = exampleAvroSerde.serializer();
            final Deserializer<Long> valueDeserializer = Serdes.Long().deserializer();
            final Deserializer<String> keyDeserializer = Serdes.String().deserializer();

            final TestInputTopic<String, LoginTime>  inputTopic = testDriver.createInputTopic(pageviewsInputTopic,
                                                                                              keySerializer,
                                                                                              exampleSerializer);

            final TestOutputTopic<String, Long> outputTopic = testDriver.createOutputTopic(outputTopicName, keyDeserializer, valueDeserializer);

            final List<LoginTime> loggedOnTimes = new ArrayList<>();
            loggedOnTimes.add(LoginTime.newBuilder().setLogintime(5L).setAppid("test-page").setUserid("user-1").build());
            loggedOnTimes.add(LoginTime.newBuilder().setLogintime(5L).setAppid("test-page").setUserid("user-2").build());
            loggedOnTimes.add(LoginTime.newBuilder().setLogintime(10L).setAppid("test-page").setUserid("user-1").build());
            loggedOnTimes.add(LoginTime.newBuilder().setLogintime(25L).setAppid("test-page").setUserid("user-3").build());
            loggedOnTimes.add(LoginTime.newBuilder().setLogintime(10L).setAppid("test-page").setUserid("user-2").build());

            List<KeyValue<String, LoginTime>> keyValues = loggedOnTimes.stream().map(o -> KeyValue.pair(o.getUserid(),o)).collect(Collectors.toList());
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
