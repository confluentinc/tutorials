package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class KafkaStreamsApplicationTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";

  @Test
  public void topologyShouldUpperCaseInputs() throws IOException {

    final Properties props = new Properties();
    try (InputStream inputStream = new FileInputStream(TEST_CONFIG_FILE)) {
        props.load(inputStream);
    }

    final String inputTopicName = props.getProperty("input.topic.name");
    final String outputTopicName = props.getProperty("output.topic.name");

    final Topology topology = KafkaStreamsApplication.buildTopology(inputTopicName, outputTopicName);

    try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, props)) {
      Serde<String> stringSerde = Serdes.String();

      final TestInputTopic<String, String> inputTopic = testDriver
              .createInputTopic(inputTopicName, stringSerde.serializer(), stringSerde.serializer());
      final TestOutputTopic<String, String> outputTopic = testDriver
              .createOutputTopic(outputTopicName, stringSerde.deserializer(), stringSerde.deserializer());

      List<String> inputs = Arrays.asList(
        "Chuck Norris can write multi-threaded applications with a single thread.",
        "No statement can catch the ChuckNorrisException.",
        "Chuck Norris can divide by zero.",
        "Chuck Norris can binary search unsorted data."
      );
      List<String> expectedOutputs = inputs.stream()
        .map(String::toUpperCase).collect(Collectors.toList());

      inputs.forEach(inputTopic::pipeInput);
      final List<String> actualOutputs = outputTopic.readValuesToList();

      assertThat(expectedOutputs, equalTo(actualOutputs));

    }

  }
}
