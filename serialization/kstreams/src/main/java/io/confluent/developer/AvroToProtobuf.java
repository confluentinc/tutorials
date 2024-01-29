package io.confluent.developer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.proto.MovieProtos;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static java.lang.Integer.parseInt;
import static java.lang.Short.parseShort;
import static org.apache.kafka.common.serialization.Serdes.Long;
import static org.apache.kafka.common.serialization.Serdes.String;

public class AvroToProtobuf {

    public static final String INPUT_TOPIC = "avro-movies";
    public static final String OUTPUT_TOPIC = "proto-movies";

    protected Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, String().getClass());
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));

        return props;
    }

    private void createTopics(Properties envProps) {
        Map<String, Object> config = new HashMap<>();

        config.put("bootstrap.servers", envProps.getProperty("bootstrap.servers"));
        AdminClient client = AdminClient.create(config);

        List<NewTopic> topics = new ArrayList<>();

        topics.add(new NewTopic(
                envProps.getProperty("input.avro.movies.topic.name"),
                parseInt(envProps.getProperty("input.avro.movies.topic.partitions")),
                parseShort(envProps.getProperty("input.avro.movies.topic.replication.factor"))));

        topics.add(new NewTopic(
                envProps.getProperty("output.proto.movies.topic.name"),
                parseInt(envProps.getProperty("output.proto.movies.topic.partitions")),
                parseShort(envProps.getProperty("output.proto.movies.topic.replication.factor"))));

        client.createTopics(topics);
        client.close();
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
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
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
