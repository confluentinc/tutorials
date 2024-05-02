package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static io.confluent.developer.StreamsToTable.INPUT_TOPIC;
import static io.confluent.developer.StreamsToTable.STREAMS_OUTPUT_TOPIC;
import static io.confluent.developer.StreamsToTable.TABLE_OUTPUT_TOPIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StreamsToTableTest {

    private final StreamsToTable streamsToTable = new StreamsToTable();

    @Test
    public void testToTable() throws IOException {

        final Properties properties = new Properties();
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("temp").toString());


        final Topology topology = streamsToTable.buildTopology(properties);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, properties)) {

            final Serializer<String> stringSerializer = Serdes.String().serializer();
            final Deserializer<String> stringDeserializer = Serdes.String().deserializer();

            final TestInputTopic<String, String> input = testDriver.createInputTopic(INPUT_TOPIC, stringSerializer, stringSerializer);
            final TestOutputTopic<String, String> streamOutputTopic = testDriver.createOutputTopic(STREAMS_OUTPUT_TOPIC, stringDeserializer, stringDeserializer);
            final TestOutputTopic<String, String> tableOutputTopic = testDriver.createOutputTopic(TABLE_OUTPUT_TOPIC, stringDeserializer, stringDeserializer);

            final List<TestRecord<String, String>> keyValues = Arrays.asList(new TestRecord<>("1", "one"), new TestRecord<>("2","two"), new TestRecord<>("3", "three"));
            final List<KeyValue<String, String>> expectedKeyValues = Arrays.asList(KeyValue.pair("1", "one"), KeyValue.pair("2","two"), KeyValue.pair("3", "three"));

            keyValues.forEach(kv -> input.pipeInput(kv.key(), kv.value()));
            final List<KeyValue<String, String>> actualStreamResults = streamOutputTopic.readKeyValuesToList();
            final List<KeyValue<String, String>> actualTableResults = tableOutputTopic.readKeyValuesToList();

            assertThat(actualStreamResults, is(expectedKeyValues));
            assertThat(actualTableResults, is(expectedKeyValues));
        }
    }

}
