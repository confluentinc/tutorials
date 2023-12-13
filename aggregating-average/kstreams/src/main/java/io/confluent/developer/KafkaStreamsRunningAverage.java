package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsRunningAverage {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsRunningAverage.class);
    public static final String INPUT_TOPIC = "average-input";
    public static final String OUTPUT_TOPIC = "average-output";

    public Topology buildTopology(Properties properties) {
        // The properties are passed in here for use with
        // StreamBuilder.build to enable any optimizations
        StreamsBuilder builder = new StreamsBuilder();

        Serde<MovieRating> movieRatingSerde = StreamsSerde.serdeFor(MovieRating.class);
        Serde<CountAndSum> countAndSumSerde = StreamsSerde.serdeFor(CountAndSum.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieRatingSerde))
                // peek statement added for visibility to Kafka Streams record processing NOT required!
                .peek((key, value) -> LOG.info(String.format("Incoming record key:[%s] value:[%s]", key, value)))
                .map((key, value) -> KeyValue.pair(value.id(), value.rating()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                .aggregate(() -> new CountAndSum(0L, 0.0),
                        (key, value, aggregate) -> {
                            double sum = value + aggregate.sum();
                            long count = aggregate.count() + 1;
                            return new CountAndSum(count, sum);
                        },
                        Materialized.with(Serdes.String(), countAndSumSerde))
                .toStream()
                .mapValues(value -> value.sum() / value.count())
                // peek statement added for visibility to Kafka Streams record processing NOT required!
                .peek((key, value) -> LOG.info(String.format("Outgoing average key:[%s] value:[%s]", key, value)))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));

        return builder.build(properties);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "running-average-application");

        KafkaStreamsRunningAverage runningAverage = new KafkaStreamsRunningAverage();
        Topology topology = runningAverage.buildTopology(properties);

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

