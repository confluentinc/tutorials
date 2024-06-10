package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
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

public class ReorderStreams {
    private static final Logger LOG = LoggerFactory.getLogger(ReorderStreams.class);
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";


    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        Serde<String> stringSerde = Serdes.String();
        Serde<Event> eventSerde = StreamsSerde.serdeFor(Event.class);
        String reorderStore = "reorder-store";
        builder.stream(INPUT, Consumed.with(stringSerde, eventSerde))
                .peek((key, value) -> LOG.info("Incoming event key[{}] value[{}]", key, value))
                .process(new ReorderingProcessorSupplier<>(reorderStore,
                        Duration.ofHours(10),
                        (k, v) -> v.eventTime(),
                        (k, v) -> v.name(),
                        Serdes.Long(),
                        eventSerde))
                .to(OUTPUT, Produced.with(stringSerde, eventSerde));

        return builder.build(allProps);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "merge-streams");
        ReorderStreams reorderStreams = new ReorderStreams();

        Topology topology = reorderStreams.buildTopology(properties);

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only don't do this in production as it wipes out all local state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
