package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static io.confluent.developer.KafkaStreamsApplication.INPUT_TOPIC;
import static io.confluent.developer.KafkaStreamsApplication.OUTPUT_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaStreamsApplicationTest {

    @Test
    @DisplayName("Kafka Streams application test")
    void kafkaStreamsTest() {
        var kafkaStreamsAppInstance = new KafkaStreamsApplication();
        var properties = new Properties();

        Topology topology = kafkaStreamsAppInstance.buildTopology(properties);
          try(TopologyTestDriver driver = new TopologyTestDriver(topology)) {
              final Serde<String> stringSerde = Serdes.String();
              final TestInputTopic<String, String> inputTopic = driver.createInputTopic(INPUT_TOPIC, stringSerde.serializer(), stringSerde.serializer());
              final TestOutputTopic<String, String> outputTopic = driver.createOutputTopic(OUTPUT_TOPIC, stringSerde.deserializer(), stringSerde.deserializer());

              inputTopic.pipeInput("foo");
              inputTopic.pipeInput("bar");
              
              final List<String> expectedOutput = Arrays.asList("FOO", "BAR");
              final List<String> actualOutput = outputTopic.readValuesToList();
              assertEquals(expectedOutput, actualOutput);
            }
    }
}