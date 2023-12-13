package io.confluent.developer;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static io.confluent.developer.KafkaStreamsRunningAverage.INPUT_TOPIC;
import static io.confluent.developer.KafkaStreamsRunningAverage.OUTPUT_TOPIC;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;


class KafkaStreamsRunningAverageTest {
  private static final MovieRating LETHAL_WEAPON_RATING_10 = new MovieRating("362", 10.0);
  private static final MovieRating LETHAL_WEAPON_RATING_8 = new MovieRating("362", 8.0);

  private final KafkaStreamsRunningAverage kafkaStreamsRunningAverage = new KafkaStreamsRunningAverage();

  @Test
  void validateAverageRating() {
    Properties properties = new Properties();

    try (TopologyTestDriver testDriver = new TopologyTestDriver(kafkaStreamsRunningAverage.buildTopology(properties));
         Serde<MovieRating> movieRatingSerde = StreamsSerde.serdeFor(MovieRating.class)) {

      TestInputTopic<String, MovieRating> inputTopic = testDriver.createInputTopic(INPUT_TOPIC,
              new StringSerializer(),
              movieRatingSerde.serializer());

      inputTopic.pipeKeyValueList(asList(
              new KeyValue<>(LETHAL_WEAPON_RATING_8.id(), LETHAL_WEAPON_RATING_8),
              new KeyValue<>(LETHAL_WEAPON_RATING_10.id(), LETHAL_WEAPON_RATING_10)
      ));

      final TestOutputTopic<String, Double> outputTopic = testDriver.createOutputTopic(OUTPUT_TOPIC,
              new StringDeserializer(),
              new DoubleDeserializer());

      final KeyValue<String, Double> expectedKeyValue = new KeyValue<>("362", 9.0);
      final KeyValue<String, Double> actualKeyValue = outputTopic.readKeyValuesToList().get(1);
      assertThat(actualKeyValue, equalTo(expectedKeyValue));
    }
  }
}