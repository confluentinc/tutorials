package io.confluent.developer;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.proto.MovieProtos;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.apache.kafka.common.serialization.Serdes.Long;

public class AvroToProtobuf {

    public static final String INPUT_TOPIC = "avro-movies";
    public static final String OUTPUT_TOPIC = "proto-movies";
    public static final String DEFAULT_PROPERTIES_LOCATION = "src/main/resources/confluent.properties";


    public static Properties loadProperties(final String path) {
        Properties properties = new Properties();
        try (InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(path))) {
            properties.load(inputStreamReader);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Problem loading properties file for example", e);
        }
    }

    public static Properties loadProperties() {
        return loadProperties(DEFAULT_PROPERTIES_LOCATION);
    }

    protected SpecificAvroSerde<Movie> movieAvroSerde(Properties envProps) {
        SpecificAvroSerde<Movie> movieAvroSerde = new SpecificAvroSerde<>();

        Map<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));
        movieAvroSerde.configure(
                serdeConfig, false);
        return movieAvroSerde;
    }

    protected KafkaProtobufSerde<MovieProtos.Movie> movieProtobufSerde(Properties envProps) {
        final KafkaProtobufSerde<MovieProtos.Movie> protobufSerde = new KafkaProtobufSerde<>();

        Map<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));
        protobufSerde.configure(
                serdeConfig, false);
        return protobufSerde;
    }

    protected Topology buildTopology(Properties properties,
                                     final SpecificAvroSerde<Movie> movieSpecificAvroSerde,
                                     final KafkaProtobufSerde<MovieProtos.Movie> movieProtoSerde) {

        properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        final StreamsBuilder builder = new StreamsBuilder();

        builder.stream(INPUT_TOPIC, Consumed.with(Long(), movieSpecificAvroSerde))
                .map((key, avroMovie) ->
                        new KeyValue<>(key, MovieProtos.Movie.newBuilder()
                                .setMovieId(avroMovie.getMovieId())
                                .setTitle(avroMovie.getTitle())
                                .setReleaseYear(avroMovie.getReleaseYear())
                                .build()))
                .to(OUTPUT_TOPIC, Produced.with(Long(), movieProtoSerde));

        return builder.build(properties);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = loadProperties(args[0]);
        } else {
            properties = loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-serialization");

        AvroToProtobuf avroToProtobuf = new AvroToProtobuf();
        Topology topology = avroToProtobuf.buildTopology(properties,
                avroToProtobuf.movieAvroSerde(properties),
                avroToProtobuf.movieProtobufSerde(properties));

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only; don't do this in production as it wipes out all local state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
