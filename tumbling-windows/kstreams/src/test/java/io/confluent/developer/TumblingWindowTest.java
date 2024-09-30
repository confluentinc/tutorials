package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.confluent.developer.TumblingWindow.INPUT_TOPIC;
import static io.confluent.developer.TumblingWindow.OUTPUT_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class TumblingWindowTest {
    private final TumblingWindow tw = new TumblingWindow();

    private List<RatingCount> readOutputTopic(TopologyTestDriver testDriver,
                                              String outputTopic,
                                              Deserializer<String> keyDeserializer,
                                              Deserializer<Long> valueDeserializer) {
        return testDriver
            .createOutputTopic(outputTopic, keyDeserializer, valueDeserializer)
            .readKeyValuesToList()
            .stream()
            .filter(Objects::nonNull)
            .map(record -> new RatingCount(record.key, record.value))
            .collect(Collectors.toList());
    }

    @Test
    public void testWindows() {
        Properties properties = new Properties();

        try (TopologyTestDriver testDriver = new TopologyTestDriver(tw.buildTopology(properties));
             Serde<MovieRating> movieRatingSerde = StreamsSerde.serdeFor(MovieRating.class)) {

            List<MovieRating> ratings = new ArrayList<>();
            ratings.add(new MovieRating("Super Mario Bros.", 1993, 3.5, "2024-09-25T11:15:00-0000"));
            ratings.add(new MovieRating("Super Mario Bros.", 1993, 2.0, "2024-09-25T11:40:00-0000"));
            ratings.add(new MovieRating("A Walk in the Clouds", 1998, 3.6, "2024-09-25T13:00:00-0000"));
            ratings.add(new MovieRating("A Walk in the Clouds", 1998, 7.1, "2024-09-25T13:01:00-0000"));
            ratings.add(new MovieRating("Die Hard", 1988, 8.2, "2024-09-25T18:00:00-0000"));
            ratings.add(new MovieRating("Die Hard", 1988, 7.6, "2024-09-25T18:05:00-0000"));
            ratings.add(new MovieRating("The Big Lebowski", 1998, 8.6, "2024-09-25T19:30:00-0000"));
            ratings.add(new MovieRating("The Big Lebowski", 1998, 7.0, "2024-09-25T19:35:00-0000"));
            ratings.add(new MovieRating("Tree of Life", 2011, 4.9, "2024-09-25T21:00:00-0000"));
            ratings.add(new MovieRating("Tree of Life", 2011, 9.9, "2024-09-25T21:11:00-0000"));

            List<RatingCount> ratingCounts = new ArrayList<>();
            ratingCounts.add(new RatingCount("Super Mario Bros.", 1L));
            ratingCounts.add(new RatingCount("Super Mario Bros.", 2L));
            ratingCounts.add(new RatingCount("A Walk in the Clouds", 1L));
            ratingCounts.add(new RatingCount("A Walk in the Clouds", 2L));
            ratingCounts.add(new RatingCount("Die Hard", 1L));
            ratingCounts.add(new RatingCount("Die Hard", 2L));
            ratingCounts.add(new RatingCount("The Big Lebowski", 1L));
            ratingCounts.add(new RatingCount("The Big Lebowski", 2L));
            ratingCounts.add(new RatingCount("Tree of Life", 1L));
            ratingCounts.add(new RatingCount("Tree of Life", 2L));

            final TestInputTopic<String, MovieRating>
                testDriverInputTopic =
                testDriver.createInputTopic(INPUT_TOPIC, new StringSerializer(), movieRatingSerde.serializer());

            for (MovieRating rating : ratings) {
                testDriverInputTopic.pipeInput(rating.title(), rating);
            }

            List<RatingCount> actualOutput = readOutputTopic(testDriver,
                OUTPUT_TOPIC,
                new StringDeserializer(),
                new LongDeserializer());

            assertEquals(ratingCounts.size(), actualOutput.size());
            for (int n = 0; n < ratingCounts.size(); n++) {
                assertEquals(ratingCounts.get(n).toString(), actualOutput.get(n).toString());
            }
        }
    }

    class RatingCount {

        private final String key;
        private final Long value;

        public RatingCount(String key, Long value) {
            this.key = key;
            this.value = value;
        }

        public String toString() {
            return key + "=" + value;
        }
    }
}
