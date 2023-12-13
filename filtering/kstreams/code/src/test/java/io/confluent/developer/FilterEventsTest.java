package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import io.confluent.developer.avro.Publication;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static java.util.Arrays.asList;

public class FilterEventsTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";

  private TopologyTestDriver testDriver;

  private SpecificAvroSerde<Publication> makeSerializer(Properties allProps) {

    SpecificAvroSerde<Publication> serde = new SpecificAvroSerde<>();

    Map<String, String> config = new HashMap<>();
    config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
    serde.configure(config, false);

    return serde;
  }

  @Test
  public void shouldFilterGRRMartinsBooks() throws IOException {
    FilterEvents fe = new FilterEvents();
    Properties allProps = fe.loadEnvProperties(TEST_CONFIG_FILE);

    String inputTopic = allProps.getProperty("input.topic.name");
    String outputTopic = allProps.getProperty("output.topic.name");

    final SpecificAvroSerde<Publication> publicationSpecificAvroSerde = makeSerializer(allProps);

    Topology topology = fe.buildTopology(allProps, publicationSpecificAvroSerde);
    testDriver = new TopologyTestDriver(topology, allProps);

    Serializer<String> keySerializer = Serdes.String().serializer();
    Deserializer<String> keyDeserializer = Serdes.String().deserializer();

    // Fixture
    Publication iceAndFire = new Publication("George R. R. Martin", "A Song of Ice and Fire");
    Publication silverChair = new Publication("C.S. Lewis", "The Silver Chair");
    Publication perelandra = new Publication("C.S. Lewis", "Perelandra");
    Publication fireAndBlood = new Publication("George R. R. Martin", "Fire & Blood");
    Publication theHobbit = new Publication("J. R. R. Tolkien", "The Hobbit");
    Publication lotr = new Publication("J. R. R. Tolkien", "The Lord of the Rings");
    Publication dreamOfSpring = new Publication("George R. R. Martin", "A Dream of Spring");
    Publication fellowship = new Publication("J. R. R. Tolkien", "The Fellowship of the Ring");
    Publication iceDragon = new Publication("George R. R. Martin", "The Ice Dragon");
    // end Fixture

    final List<Publication>
        input = asList(iceAndFire, silverChair, perelandra, fireAndBlood, theHobbit, lotr, dreamOfSpring, fellowship,
                       iceDragon);

    final List<Publication> expectedOutput = asList(iceAndFire, fireAndBlood, dreamOfSpring, iceDragon);

    testDriver.createInputTopic(inputTopic, keySerializer, publicationSpecificAvroSerde.serializer())
        .pipeValueList(input);

    List<Publication> actualOutput =
        testDriver
            .createOutputTopic(outputTopic, keyDeserializer, publicationSpecificAvroSerde.deserializer())
            .readValuesToList()
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    Assert.assertEquals(expectedOutput, actualOutput);
  }

  @After
  public void cleanup() {
    testDriver.close();
  }

}
