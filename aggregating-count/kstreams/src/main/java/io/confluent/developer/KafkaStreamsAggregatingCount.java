package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsAggregatingCount {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsAggregatingCount.class);
    public static final String INPUT_TOPIC = "aggregation-count-input" ;
    public static final String OUTPUT_TOPIC = "aggregation-count-output";
    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final Serde<TicketSale> ticketSaleSerde = StreamsSerde.serdeFor(TicketSale.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), ticketSaleSerde))
                .peek((key, value) -> LOG.info("Incoming records key[{}] value[{}]", key, value))
                .map((k, v) -> new KeyValue<>(v.title(), v.ticketTotalValue()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
                .count()
                .toStream().mapValues(v -> v + " tickets sold")
                .peek((key, value) -> LOG.info("Count results key[{}] value[{}]", key, value))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        return builder.build(allProps);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-aggregating-count");

        KafkaStreamsAggregatingCount kafkaStreamsAggregatingCount = new KafkaStreamsAggregatingCount();
        Topology topology = kafkaStreamsAggregatingCount.buildTopology(properties);

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
