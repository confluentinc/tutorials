package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class TransformStream {

    private static final Logger LOG = LoggerFactory.getLogger(TransformStream.class);
    public static final String INPUT_TOPIC = "raw-movies";
    public static final String OUTPUT_TOPIC = "movies";

    public Topology buildTopology(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        final StreamsBuilder builder = new StreamsBuilder();

        Serde<RawMovie> rawMovieSerde = StreamsSerde.serdeFor(RawMovie.class);
        Serde<Movie> movieSerde = StreamsSerde.serdeFor(Movie.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), rawMovieSerde))
            // peek statement added for visibility to Kafka Streams record processing NOT required!
            .peek((key, value) -> LOG.info(String.format("Incoming record key:[%s] value:[%s]", key, value)))
            .map((key, rawMovie) -> new KeyValue<>(rawMovie.id(), convertRawMovie(rawMovie)))
            // peek statement added for visibility to Kafka Streams record processing NOT required!
            .peek((key, value) -> LOG.info("Transformed results key[{}] value[{}]", key, value))
            .to(OUTPUT_TOPIC, Produced.with(Serdes.Long(), movieSerde));

        return builder.build(properties);
    }

    public static Movie convertRawMovie(RawMovie rawMovie) {
        String[] titleParts = rawMovie.title().split("::");
        String title = titleParts[0];
        int releaseYear = Integer.parseInt(titleParts[1]);
        return new Movie(rawMovie.id(), title, releaseYear, rawMovie.genre());
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-transforming");

        TransformStream transformStream = new TransformStream();
        Topology topology = transformStream.buildTopology(properties);

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
