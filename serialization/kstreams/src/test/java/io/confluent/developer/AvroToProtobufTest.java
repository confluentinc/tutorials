package io.confluent.developer;

import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.proto.MovieProtos;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;

import static io.confluent.developer.AvroToProtobuf.INPUT_TOPIC;
import static io.confluent.developer.AvroToProtobuf.OUTPUT_TOPIC;
import static io.confluent.developer.proto.MovieProtos.Movie.newBuilder;
import static org.apache.kafka.common.serialization.Serdes.Long;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class AvroToProtobufTest {

    private final AvroToProtobuf avroToProtobuf = new AvroToProtobuf();

    @Test
    public void shouldChangeSerializationFormat() throws IOException {
        Properties properties = new Properties();

        // set a different mock SR URL for each Serde because otherwise it will only get instantiated
        // with the first (Avro) provider
        properties.put("schema.registry.url", "mock://SR_DUMMY_URL_AVRO:8081");
        final SpecificAvroSerde<Movie> avroSerde = avroToProtobuf.movieAvroSerde(properties);

        properties.put("schema.registry.url", "mock://SR_DUMMY_URL_PROTOBUF:8081");
        final KafkaProtobufSerde<MovieProtos.Movie> protobufSerde = avroToProtobuf.movieProtobufSerde(properties);

        try (TopologyTestDriver testDriver = new TopologyTestDriver(avroToProtobuf.buildTopology(properties, avroSerde, protobufSerde))) {

            testDriver
                    .createInputTopic(INPUT_TOPIC, Long().serializer(), avroSerde.serializer())
                    .pipeValueList(this.prepareInputFixture());

            final List<MovieProtos.Movie> moviesProto =
                    testDriver.createOutputTopic(OUTPUT_TOPIC, Long().deserializer(), protobufSerde.deserializer())
                            .readValuesToList();

            assertThat(moviesProto, equalTo(expectedMovies()));
        }
    }

    /**
     * Prepares expected movies in protobuf format
     *
     * @return a list of three (3) movie
     */
    private List<MovieProtos.Movie> expectedMovies() {
        List<MovieProtos.Movie> movieList = new java.util.ArrayList<>();
        movieList.add(newBuilder().setMovieId(1L).setTitle("Lethal Weapon").setReleaseYear(1992).build());
        movieList.add(newBuilder().setMovieId(2L).setTitle("Die Hard").setReleaseYear(1988).build());
        movieList.add(newBuilder().setMovieId(3L).setTitle("Predator").setReleaseYear(1987).build());
        return movieList;
    }

    /**
     * Prepares test data in AVRO format
     *
     * @return a list of three (3) movies
     */
    private List<Movie> prepareInputFixture() {
        List<Movie> movieList = new java.util.ArrayList<>();
        movieList.add(new Movie(1L, "Lethal Weapon", 1992));
        movieList.add(new Movie(2L, "Die Hard", 1988));
        movieList.add(new Movie(3L, "Predator", 1987));
        return movieList;
    }
}
