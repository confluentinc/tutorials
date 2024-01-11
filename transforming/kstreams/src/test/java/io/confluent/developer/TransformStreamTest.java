package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.confluent.developer.TransformStream.INPUT_TOPIC;
import static io.confluent.developer.TransformStream.OUTPUT_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransformStreamTest {

    private final TransformStream transformStream = new TransformStream();


    private List<Movie> readOutputTopic(TopologyTestDriver testDriver,
                                        String topic,
                                        Deserializer<String> keyDeserializer,
                                        Deserializer<Movie> valueDeserializer) {

        return testDriver
            .createOutputTopic(topic, keyDeserializer, valueDeserializer)
            .readKeyValuesToList()
            .stream()
            .filter(Objects::nonNull)
            .map(record -> record.value)
            .collect(Collectors.toList());
    }

    @Test
    public void testMovieConverter() {
        Movie movie = TransformStream.convertRawMovie(new RawMovie(294L, "Tree of Life::2011", "drama"));
        assertNotNull(movie);
        assertEquals(294L, movie.id());
        assertEquals("Tree of Life", movie.title());
        assertEquals(2011, movie.release_year());
        assertEquals("drama", movie.genre());
    }


    @Test
    public void testTransformStream() throws IOException {
        Properties properties = new Properties();

        try (TopologyTestDriver testDriver = new TopologyTestDriver(transformStream.buildTopology(properties));
             Serde<RawMovie> rawMovieSerde = StreamsSerde.serdeFor(RawMovie.class);
             Serde<Movie> movieSerde = StreamsSerde.serdeFor(Movie.class)) {


            List<RawMovie> input = new ArrayList<>();
            input.add(new RawMovie(294, "Die Hard::1988", "action"));
            input.add(new RawMovie(354, "Tree of Life::2011", "drama"));
            input.add(new RawMovie(782, "A Walk in the Clouds::1995", "romance"));
            input.add(new RawMovie(128, "The Big Lebowski::1998", "comedy"));

            List<Movie> expectedOutput = new ArrayList<>();
            expectedOutput.add(new Movie(294, "Die Hard", 1988, "action"));
            expectedOutput.add(new Movie(354, "Tree of Life", 2011, "drama"));
            expectedOutput.add(new Movie(782, "A Walk in the Clouds", 1995, "romance"));
            expectedOutput.add(new Movie(128, "The Big Lebowski", 1998, "comedy"));

            final TestInputTopic<String, RawMovie>
                testDriverInputTopic =
                testDriver.createInputTopic(INPUT_TOPIC, new StringSerializer(), rawMovieSerde.serializer());

            for (RawMovie rawMovie : input) {
                testDriverInputTopic.pipeInput(rawMovie.title(), rawMovie);
            }
            List<Movie> actualOutput = readOutputTopic(testDriver, OUTPUT_TOPIC, new StringDeserializer(), movieSerde.deserializer());

            assertEquals(expectedOutput, actualOutput);
        }
    }

}
