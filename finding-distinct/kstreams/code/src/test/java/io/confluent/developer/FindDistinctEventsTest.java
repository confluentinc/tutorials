package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import io.confluent.developer.avro.Click;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static java.util.Arrays.asList;

public class FindDistinctEventsTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";
  private final static Path STATE_DIR =
      Paths.get(System.getProperty("user.dir"), "build");

  private final Properties allProps;

  public FindDistinctEventsTest() throws IOException {
    allProps = FindDistinctEvents.loadEnvProperties(TEST_CONFIG_FILE);
    allProps.put(StreamsConfig.STATE_DIR_CONFIG, STATE_DIR.toString());
  }

  private static SpecificAvroSerde<Click> makeSerializer(Properties allProps) {
    SpecificAvroSerde<Click> serde = new SpecificAvroSerde<>();
    
    Map<String, String> config = new HashMap<>();
    config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
    serde.configure(config, false);

    return serde;
  }

  @Test
  public void shouldFilterDistinctEvents() {

    final FindDistinctEvents distinctifier = new FindDistinctEvents();

    String inputTopic = allProps.getProperty("input.topic.name");
    String outputTopic = allProps.getProperty("output.topic.name");

    final SpecificAvroSerde<Click> clickSerde = makeSerializer(allProps);

    Topology topology = distinctifier.buildTopology(allProps, clickSerde);
    final List<Click> expectedOutput;
    List<Click> actualOutput;
    try (TopologyTestDriver testDriver = new TopologyTestDriver(topology, allProps)) {

      Serializer<String> keySerializer = Serdes.String().serializer();

      final List<Click> clicks = asList(
          new Click("10.0.0.1",
                    "https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html",
                    "2019-09-16T14:53:43+00:00"),
          new Click("10.0.0.2",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2019-09-16T14:53:43+00:01"),
          new Click("10.0.0.3",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2019-09-16T14:53:43+00:03"),
          new Click("10.0.0.1",
                    "https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html",
                    "2019-09-16T14:53:43+00:00"),
          new Click("10.0.0.2",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2019-09-16T14:53:43+00:01"),
          new Click("10.0.0.3",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2019-09-16T14:53:43+00:03"));

      final TestInputTopic<String, Click>
          testDriverInputTopic =
          testDriver.createInputTopic(inputTopic, keySerializer, clickSerde.serializer());

      clicks.forEach(clk -> testDriverInputTopic.pipeInput(clk.getIp(), clk));

      expectedOutput = asList(clicks.get(0), clicks.get(1), clicks.get(2));

      Deserializer<String> keyDeserializer = Serdes.String().deserializer();
      actualOutput =
          testDriver.createOutputTopic(outputTopic, keyDeserializer, clickSerde.deserializer()).readValuesToList()
              .stream().filter(
              Objects::nonNull).collect(Collectors.toList());
    }

    Assert.assertEquals(expectedOutput, actualOutput);
  }
}
