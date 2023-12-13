package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.avro.RatedMovie;
import io.confluent.developer.avro.Rating;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static org.junit.Assert.assertEquals;

public class JoinStreamToTableTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";
    private TopologyTestDriver testDriver;

    private SpecificAvroSerializer<Movie> makeMovieSerializer(Properties allProps) {
        SpecificAvroSerializer<Movie> serializer = new SpecificAvroSerializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        serializer.configure(config, false);

        return serializer;
    }

    private SpecificAvroSerializer<Rating> makeRatingSerializer(Properties allProps) {
        SpecificAvroSerializer<Rating> serializer = new SpecificAvroSerializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        serializer.configure(config, false);

        return serializer;
    }

    private SpecificAvroDeserializer<RatedMovie> makeRatedMovieDeserializer(Properties allProps) {
        SpecificAvroDeserializer<RatedMovie> deserializer = new SpecificAvroDeserializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        deserializer.configure(config, false);

        return deserializer;
    }

    private List<RatedMovie> readOutputTopic(TopologyTestDriver testDriver,
                                             String topic,
                                             Deserializer<String> keyDeserializer,
                                             SpecificAvroDeserializer<RatedMovie> makeRatedMovieDeserializer) {
        List<RatedMovie> results = new ArrayList<>();
        final TestOutputTopic<String, RatedMovie>
            testOutputTopic =
            testDriver.createOutputTopic(topic, keyDeserializer, makeRatedMovieDeserializer);
        testOutputTopic
            .readKeyValuesToList()
            .forEach(record -> {
                         if (record != null) {
                             results.add(record.value);
                         }
                     }
            );
        return results;
    }

    @Test
    public void testJoin() throws IOException {
        JoinStreamToTable jst = new JoinStreamToTable();
        Properties allProps = jst.loadEnvProperties(TEST_CONFIG_FILE);

        String tableTopic = allProps.getProperty("movie.topic.name");
        String streamTopic = allProps.getProperty("rating.topic.name");
        String outputTopic = allProps.getProperty("rated.movies.topic.name");
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);

        Topology topology = jst.buildTopology(allProps);
        testDriver = new TopologyTestDriver(topology, allProps);

        Serializer<String> keySerializer = Serdes.String().serializer();
        SpecificAvroSerializer<Movie> movieSerializer = makeMovieSerializer(allProps);
        SpecificAvroSerializer<Rating> ratingSerializer = makeRatingSerializer(allProps);

        Deserializer<String> stringDeserializer = Serdes.String().deserializer();
        SpecificAvroDeserializer<RatedMovie> valueDeserializer = makeRatedMovieDeserializer(allProps);

        List<Movie> movies = new ArrayList<>();
        movies.add(Movie.newBuilder().setId(294).setTitle("Die Hard").setReleaseYear(1988).build());
        movies.add(Movie.newBuilder().setId(354).setTitle("Tree of Life").setReleaseYear(2011).build());
        movies.add(Movie.newBuilder().setId(782).setTitle("A Walk in the Clouds").setReleaseYear(1998).build());
        movies.add(Movie.newBuilder().setId(128).setTitle("The Big Lebowski").setReleaseYear(1998).build());
        movies.add(Movie.newBuilder().setId(780).setTitle("Super Mario Bros.").setReleaseYear(1993).build());

        List<Rating> ratings = new ArrayList<>();
        ratings.add(Rating.newBuilder().setId(294).setRating(8.2).build());
        ratings.add(Rating.newBuilder().setId(294).setRating(8.5).build());
        ratings.add(Rating.newBuilder().setId(354).setRating(9.9).build());
        ratings.add(Rating.newBuilder().setId(354).setRating(9.7).build());
        ratings.add(Rating.newBuilder().setId(782).setRating(7.8).build());
        ratings.add(Rating.newBuilder().setId(782).setRating(7.7).build());
        ratings.add(Rating.newBuilder().setId(128).setRating(8.7).build());
        ratings.add(Rating.newBuilder().setId(128).setRating(8.4).build());
        ratings.add(Rating.newBuilder().setId(780).setRating(2.1).build());

        List<RatedMovie> ratedMovies = new ArrayList<>();
        ratedMovies.add(RatedMovie.newBuilder().setTitle("Die Hard").setId(294).setReleaseYear(1988).setRating(8.2).build());
        ratedMovies.add(RatedMovie.newBuilder().setTitle("Die Hard").setId(294).setReleaseYear(1988).setRating(8.5).build());
        ratedMovies.add(RatedMovie.newBuilder().setTitle("Tree of Life").setId(354).setReleaseYear(2011).setRating(9.9).build());
        ratedMovies.add(RatedMovie.newBuilder().setTitle("Tree of Life").setId(354).setReleaseYear(2011).setRating(9.7).build());
        ratedMovies.add(RatedMovie.newBuilder().setId(782).setTitle("A Walk in the Clouds").setReleaseYear(1998).setRating(7.8).build());
        ratedMovies.add(RatedMovie.newBuilder().setId(782).setTitle("A Walk in the Clouds").setReleaseYear(1998).setRating(7.7).build());
        ratedMovies.add(RatedMovie.newBuilder().setId(128).setTitle("The Big Lebowski").setReleaseYear(1998).setRating(8.7).build());
        ratedMovies.add(RatedMovie.newBuilder().setId(128).setTitle("The Big Lebowski").setReleaseYear(1998).setRating(8.4).build());
        ratedMovies.add(RatedMovie.newBuilder().setId(780).setTitle("Super Mario Bros.").setReleaseYear(1993).setRating(2.1).build());

        final TestInputTopic<String, Movie>
            movieTestInputTopic = testDriver.createInputTopic(tableTopic, keySerializer, movieSerializer);

        for (Movie movie : movies) {
            movieTestInputTopic.pipeInput(String.valueOf(movie.getId()), movie);
        }

        final TestInputTopic<String, Rating>
            ratingTestInputTopic =
            testDriver.createInputTopic(streamTopic, keySerializer, ratingSerializer);
        for (Rating rating : ratings) {
            ratingTestInputTopic.pipeInput(String.valueOf(rating.getId()), rating);
        }

        List<RatedMovie> actualOutput = readOutputTopic(testDriver, outputTopic, stringDeserializer, valueDeserializer);

        assertEquals(ratedMovies, actualOutput);
    }

    @After
    public void cleanup() {
        testDriver.close();
    }

}
