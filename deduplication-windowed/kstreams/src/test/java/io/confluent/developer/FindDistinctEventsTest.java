package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FindDistinctEventsTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";
  private final static Path STATE_DIR =
      Paths.get(System.getProperty("user.dir"), "build");

  private final Properties allProps = new Properties();


 @Test
 void shouldFilterDistinctEvents() {

    final FindDistinctEvents distinctifier = new FindDistinctEvents();

    String inputTopic = FindDistinctEvents.INPUT_TOPIC;
    String outputTopic = FindDistinctEvents.OUTPUT_TOPIC;

    final Serde<Click> clickSerde = StreamsSerde.serdeFor(Click.class);

    Topology topology = distinctifier.buildTopology(allProps);
    final List<Click> expectedOutput;
    List<Click> actualOutput;
    try (TopologyTestDriver testDriver = new TopologyTestDriver(topology, allProps)) {

      Serializer<String> keySerializer = Serdes.String().serializer();

      final List<Click> clicks = asList(
          new Click("10.0.0.1",
                    "https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html",
                    "2024-09-16T14:53:43+00:00"),
          new Click("10.0.0.2",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2024-09-16T14:53:43+00:01"),
          new Click("10.0.0.3",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2024-09-16T14:53:43+00:03"),
          new Click("10.0.0.1",
                    "https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html",
                    "2024-09-16T14:53:43+00:00"),
          new Click("10.0.0.2",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2024-09-16T14:53:43+00:01"),
          new Click("10.0.0.3",
                    "https://www.confluent.io/hub/confluentinc/kafka-connect-datagen",
                    "2024-09-16T14:53:43+00:03"));

      final TestInputTopic<String, Click>
          testDriverInputTopic =
          testDriver.createInputTopic(inputTopic, keySerializer, clickSerde.serializer());

      clicks.forEach(clk -> testDriverInputTopic.pipeInput(clk.ip(), clk));

      expectedOutput = asList(clicks.get(0), clicks.get(1), clicks.get(2));

      Deserializer<String> keyDeserializer = Serdes.String().deserializer();
      actualOutput =
          testDriver.createOutputTopic(outputTopic, keyDeserializer, clickSerde.deserializer()).readValuesToList()
              .stream().filter(
              Objects::nonNull).collect(Collectors.toList());
    }

    assertEquals(expectedOutput, actualOutput);
  }
}
