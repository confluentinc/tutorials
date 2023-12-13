package io.confluent.developer.serialization;

import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.proto.MovieProtos;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;

import static io.confluent.developer.proto.MovieProtos.Movie.newBuilder;
import static org.apache.kafka.common.serialization.Serdes.Long;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class SerializationTutorialTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";

  @Test
  public void shouldChangeSerializationFormat() throws IOException {
    SerializationTutorial tutorial = new SerializationTutorial();
    final Properties envProps = tutorial.loadEnvProperties(TEST_CONFIG_FILE);
    final Properties streamsProps = tutorial.buildStreamsProperties(envProps);

    String inputTopicName = envProps.getProperty("input.avro.movies.topic.name");
    String outputTopicName = envProps.getProperty("output.proto.movies.topic.name");

    final SpecificAvroSerde<Movie> avroSerde = tutorial.movieAvroSerde(envProps);
    final KafkaProtobufSerde<MovieProtos.Movie> protobufSerde = tutorial.movieProtobufSerde(envProps);
    
    Topology topology = tutorial.buildTopology(envProps, avroSerde, protobufSerde);
    streamsProps.put("statestore.cache.max.bytes", 0);
    TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamsProps);

    testDriver
        .createInputTopic(inputTopicName, Long().serializer(), avroSerde.serializer())
        .pipeValueList(this.prepareInputFixture());

    final List<MovieProtos.Movie> moviesProto =
        testDriver.createOutputTopic(outputTopicName, Long().deserializer(), protobufSerde.deserializer())
            .readValuesToList();

    assertThat(moviesProto, equalTo(expectedMovies()));
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
