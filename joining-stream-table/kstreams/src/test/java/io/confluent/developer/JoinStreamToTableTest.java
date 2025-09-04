package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinStreamToTableTest {

    @Test
    @DisplayName("Should join movie table with ratings stream")
    void testJoin() {
        JoinStreamToTable jst = new JoinStreamToTable();
        Properties allProps = new Properties();

        String tableTopic = JoinStreamToTable.MOVIE_INPUT_TOPIC;
        String streamTopic = JoinStreamToTable.RATING_INPUT_TOPIC;
        String outputTopic = JoinStreamToTable.RATED_MOVIES_OUTPUT;
        Topology topology = jst.buildTopology(allProps);
        try (TopologyTestDriver driver = new TopologyTestDriver(topology, allProps)) {

            Serde<Movie> movieSerde = StreamsSerde.serdeFor(Movie.class);
            Serde<Rating> ratingSerde = StreamsSerde.serdeFor(Rating.class);
            Serde<RatedMovie> ratedMovieSerde = StreamsSerde.serdeFor(RatedMovie.class);

            Serializer<String> keySerializer = Serdes.String().serializer();
            Serializer<Movie> movieSerializer = movieSerde.serializer();
            Serializer<Rating> ratingSerializer = ratingSerde.serializer();
            Deserializer<String> keyDeserializer = Serdes.String().deserializer();
            Deserializer<RatedMovie> valueDeserializer = ratedMovieSerde.deserializer();

            List<Movie> movies = getMovies();
            List<Rating> ratings = getRatings();
            List<RatedMovie> expectedRatedMovies = getRatedMovies();

            final TestInputTopic<String, Movie> movieTestInputTopic = driver.createInputTopic(tableTopic, keySerializer, movieSerializer);
            final TestInputTopic<String, Rating> ratingTestInputTopic = driver.createInputTopic(streamTopic, keySerializer, ratingSerializer);
            final TestOutputTopic<String, RatedMovie> ratedMovieTestOutputTopic = driver.createOutputTopic(outputTopic, keyDeserializer, valueDeserializer);

            for (Movie movie : movies) {
                movieTestInputTopic.pipeInput(movie.id(), movie);
            }
            for (Rating rating : ratings) {
                ratingTestInputTopic.pipeInput(rating.id(), rating);
            }

            List<RatedMovie> actualOutput = ratedMovieTestOutputTopic.readValuesToList();

            assertEquals(expectedRatedMovies, actualOutput);
        }
    }

    private static List<Movie> getMovies() {
        List<Movie> movies = new ArrayList<>();
        movies.add(new Movie("294", "Die Hard", 1988));
        movies.add(new Movie("354", "Tree of Life", 2011));
        movies.add(new Movie("782", "A Walk in the Clouds", 1998));
        movies.add(new Movie("128", "The Big Lebowski", 1998));
        movies.add(new Movie("780", "Super Mario Bros.", 1993));
        return movies;
    }

    private static List<Rating> getRatings() {
        List<Rating> ratings = new ArrayList<>();
        ratings.add(new Rating("294", 8.2));
        ratings.add(new Rating("294", 8.5));
        ratings.add(new Rating("354", 9.9));
        ratings.add(new Rating("354", 9.7));
        ratings.add(new Rating("782", 7.8));
        ratings.add(new Rating("782", 7.7));
        ratings.add(new Rating("128", 8.7));
        ratings.add(new Rating("128", 8.4));
        ratings.add(new Rating("780", 2.1));
        return ratings;
    }

    private static List<RatedMovie> getRatedMovies() {
        List<RatedMovie> expectedRatedMovies = new ArrayList<>();
        expectedRatedMovies.add(new RatedMovie("294", "Die Hard", 1988, 8.2));
        expectedRatedMovies.add(new RatedMovie("294", "Die Hard", 1988, 8.5));
        expectedRatedMovies.add(new RatedMovie("354", "Tree of Life", 2011, 9.9));
        expectedRatedMovies.add(new RatedMovie("354", "Tree of Life", 2011, 9.7));
        expectedRatedMovies.add(new RatedMovie("782", "A Walk in the Clouds", 1998, 7.8));
        expectedRatedMovies.add(new RatedMovie("782", "A Walk in the Clouds", 1998, 7.7));
        expectedRatedMovies.add(new RatedMovie("128", "The Big Lebowski", 1998, 8.7));
        expectedRatedMovies.add(new RatedMovie("128", "The Big Lebowski", 1998, 8.4));
        expectedRatedMovies.add(new RatedMovie("780", "Super Mario Bros.", 1993, 2.1));
        return expectedRatedMovies;
    }
}
