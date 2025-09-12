package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;


public class TumblingWindow {

    private static final Logger LOG = LoggerFactory.getLogger(TumblingWindow.class);
    public static final String INPUT_TOPIC = "ratings";
    public static final String OUTPUT_TOPIC = "rating-counts";

    public Topology buildTopology(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, RatingTimestampExtractor.class.getName());
        properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        final StreamsBuilder builder = new StreamsBuilder();

        Serde<MovieRating> movieRatingSerde = StreamsSerde.serdeFor(MovieRating.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieRatingSerde))
            // peek statement added for visibility to Kafka Streams record processing NOT required!
            .peek((key, value) -> LOG.info(String.format("Incoming record key:[%s] value:[%s]", key, value)))
            .map((key, rating) -> new KeyValue<>(rating.title(), rating))
            .groupByKey(Grouped.with(Serdes.String(), movieRatingSerde))
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(10), Duration.ofMinutes(1440)))
            .count()
            .toStream()
            .map((Windowed<String> key, Long count) -> new KeyValue<>(key.key(), count.intValue()))
            // peek statement added for visibility to Kafka Streams record processing NOT required!
            .peek((key, value) -> LOG.info(String.format("Outgoing average key:[%s] value:[%s]", key, value)))
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Integer()));

        return builder.build(properties);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "tumbling-window-application");

        TumblingWindow tumblingWindow = new TumblingWindow();
        Topology topology = tumblingWindow.buildTopology(properties);

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
