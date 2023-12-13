package io.confluent.developer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.Test;

public class StreamsToTableTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";

  @Test
  public void testToTable() throws IOException {
     final StreamsToTable streamsToTable = new StreamsToTable();

     final Properties allProps = new Properties();
     try (InputStream inputStream = new FileInputStream(TEST_CONFIG_FILE)) {
         allProps.load(inputStream);
     }
     allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
     allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
     allProps.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("temp").toString());

    final String inputTopic = allProps.getProperty("input.topic.name");
    final String streamsOutputTopicName = allProps.getProperty("streams.output.topic.name");
    final String tableOutputTopicName = allProps.getProperty("table.output.topic.name");

    final Topology topology = streamsToTable.buildTopology(allProps);
    try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, allProps)) {

      final Serializer<String> stringSerializer = Serdes.String().serializer();
      final Deserializer<String> stringDeserializer = Serdes.String().deserializer();

      final TestInputTopic<String, String> input = testDriver.createInputTopic(inputTopic, stringSerializer, stringSerializer);
      final TestOutputTopic<String, String> streamOutputTopic = testDriver.createOutputTopic(streamsOutputTopicName, stringDeserializer, stringDeserializer);
      final TestOutputTopic<String, String> tableOutputTopic = testDriver.createOutputTopic(tableOutputTopicName, stringDeserializer, stringDeserializer);

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
