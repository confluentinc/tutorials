package io.confluent.developer.connect.jdbc.specificavro;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import io.confluent.developer.avro.City;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static java.util.Arrays.asList;

public class StreamsIngestTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";

  private SpecificAvroSerde<City> makeSerializer(Properties envProps) {
    SpecificAvroSerde<City> serde = new SpecificAvroSerde<>();

    Map<String, String> config = new HashMap<>();
    config.put("schema.registry.url", envProps.getProperty("schema.registry.url"));
    serde.configure(config, false);

    return serde;
  }

  @Test
  public void shouldCreateKeyedStream() throws IOException {
    StreamsIngest si = new StreamsIngest();
    Properties envProps = si.loadEnvProperties(TEST_CONFIG_FILE);
    Properties streamProps = si.buildStreamsProperties(envProps);

    String inputTopic = envProps.getProperty("input.topic.name");
    String outputTopic = envProps.getProperty("output.topic.name");

    final SpecificAvroSerde<City> citySpecificAvroSerde = makeSerializer(envProps);

    Topology topology = si.buildTopology(envProps, citySpecificAvroSerde);
    final List<Long> expectedOutput;
    List<Long> actualOutput;
    try (TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps)) {

      Serializer<String> keySerializer = Serdes.String().serializer();
      Deserializer<Long> keyDeserializer = Serdes.Long().deserializer();

      final TestInputTopic<String, City>
          driverInputTopic =
          testDriver.createInputTopic(inputTopic, keySerializer, citySpecificAvroSerde.serializer());

      // Fixture
      City c1 = new City(1L, "Raleigh", "NC");
      City c2 = new City(2L, "Mountain View", "CA");
      City c3 = new City(3L, "Knoxville", "TN");
      City c4 = new City(4L, "Houston", "TX");
      City c5 = new City(5L, "Olympia", "WA");
      City c6 = new City(6L, "Bismarck", "ND");
      // end Fixture

      final List<City>
          input = asList(c1, c2, c3, c4, c5, c6);

      expectedOutput = asList(1L, 2L, 3L, 4L, 5L, 6L);

      for (City city : input) {
        driverInputTopic.pipeInput(null, city);
      }

      actualOutput = testDriver.createOutputTopic(outputTopic, keyDeserializer, citySpecificAvroSerde.deserializer())
          .readKeyValuesToList()
          .stream()
          .filter(longCityKeyValue -> longCityKeyValue.key != null)
          .map(record -> record.key)
          .collect(Collectors.toList());
      
      Assert.assertEquals(expectedOutput, actualOutput);
    }
  }

}
