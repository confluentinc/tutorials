package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import io.confluent.developer.avro.Rating;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;

import static org.junit.Assert.assertEquals;

public class TumblingWindowTest {
    private final static String TEST_CONFIG_FILE = "configuration/test.properties";
    private TopologyTestDriver testDriver;


    private SpecificAvroSerializer<Rating> makeRatingSerializer(Properties allProps) {
        SpecificAvroSerializer<Rating> serializer = new SpecificAvroSerializer<>();

        Map<String, String> config = new HashMap<>();
        for (final String name: allProps.stringPropertyNames())
                    config.put(name, allProps.getProperty(name));
        serializer.configure(config, false);

        return serializer;
    }

    private List<RatingCount> readOutputTopic(TopologyTestDriver testDriver,
                                              String outputTopic,
                                              Deserializer<String> keyDeserializer,
                                              Deserializer<String> valueDeserializer) {
        return testDriver
            .createOutputTopic(outputTopic, keyDeserializer, valueDeserializer)
            .readKeyValuesToList()
            .stream()
            .filter(Objects::nonNull)
            .map(record -> new RatingCount(record.key, record.value))
            .collect(Collectors.toList());
    }

    @Test
    public void testWindows() throws IOException {
        TumblingWindow tw = new TumblingWindow();
        Properties allProps = tw.buildStreamsProperties(tw.loadEnvProperties(TEST_CONFIG_FILE));

        String inputTopic = allProps.getProperty("rating.topic.name");
        String outputTopic = allProps.getProperty("rating.count.topic.name");

        Topology topology = tw.buildTopology(allProps);
        testDriver = new TopologyTestDriver(topology, allProps);

        Serializer<String> stringSerializer = Serdes.String().serializer();
        SpecificAvroSerializer<Rating> ratingSerializer = makeRatingSerializer(allProps);
        Deserializer<String> stringDeserializer = Serdes.String().deserializer();

        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.newBuilder().setTitle("Super Mario Bros.").setReleaseYear(1993).setRating(3.5).setTimestamp("2019-04-25T11:15:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("Super Mario Bros.").setReleaseYear(1993).setRating(2.0).setTimestamp("2019-04-25T11:40:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("A Walk in the Clouds").setReleaseYear(1998).setRating(3.6).setTimestamp("2019-04-25T13:00:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("A Walk in the Clouds").setReleaseYear(1998).setRating(7.1).setTimestamp("2019-04-25T13:01:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("Die Hard").setReleaseYear(1988).setRating(8.2).setTimestamp("2019-04-25T18:00:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("Die Hard").setReleaseYear(1988).setRating(7.6).setTimestamp("2019-04-25T18:05:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("The Big Lebowski").setReleaseYear(1998).setRating(8.6).setTimestamp("2019-04-25T19:30:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("The Big Lebowski").setReleaseYear(1998).setRating(7.0).setTimestamp("2019-04-25T19:35:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("Tree of Life").setReleaseYear(2011).setRating(4.9).setTimestamp("2019-04-25T21:00:00-0000").build());
        ratings.add(Rating.newBuilder().setTitle("Tree of Life").setReleaseYear(2011).setRating(9.9).setTimestamp("2019-04-25T21:11:00-0000").build());

        List<RatingCount> ratingCounts = new ArrayList<>();
        ratingCounts.add(new RatingCount("Super Mario Bros.", "1"));
        ratingCounts.add(new RatingCount("Super Mario Bros.", "1"));
        ratingCounts.add(new RatingCount("A Walk in the Clouds", "1"));
        ratingCounts.add(new RatingCount("A Walk in the Clouds", "2"));
        ratingCounts.add(new RatingCount("Die Hard", "1"));
        ratingCounts.add(new RatingCount("Die Hard", "2"));
        ratingCounts.add(new RatingCount("The Big Lebowski", "1"));
        ratingCounts.add(new RatingCount("The Big Lebowski", "2"));
        ratingCounts.add(new RatingCount("Tree of Life", "1"));
        ratingCounts.add(new RatingCount("Tree of Life", "1"));

        final TestInputTopic<String, Rating>
            testDriverInputTopic =
            testDriver.createInputTopic(inputTopic, stringSerializer, ratingSerializer);

        for (Rating rating : ratings) {
            testDriverInputTopic.pipeInput(rating.getTitle(), rating);
        }

        List<RatingCount> actualOutput = readOutputTopic(testDriver,
                                                         outputTopic,
                                                         stringDeserializer,
                                                         stringDeserializer);

        assertEquals(ratingCounts.size(), actualOutput.size());
        for(int n = 0; n < ratingCounts.size(); n++) {
            assertEquals(ratingCounts.get(n).toString(), actualOutput.get(n).toString());
        }
    }

    @After
    public void cleanup() {
        testDriver.close();
    }
}

class RatingCount {

    private final String key;
    private final String value;

    public RatingCount(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String toString() {
        return key + "=" + value;
    }
}
