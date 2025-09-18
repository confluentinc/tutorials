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

public class FilterEvents {

    private static final Logger LOG = LoggerFactory.getLogger(FilterEvents.class);
    public static final String INPUT_TOPIC = "filtering-input";
    public static final String OUTPUT_TOPIC = "filtering-output";


    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final Serde<Publication> publicationSerde = StreamsSerde.serdeFor(Publication.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), publicationSerde))
                .peek((key, value) -> LOG.info("Incoming record key[{}] value[{}]", key, value))
                .filter((name, publication) -> "George R. R. Martin".equals(publication.name()))
                .peek((key, value) -> LOG.info("Filtered record key[{}] value[{}]", key, value))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), publicationSerde));

        return builder.build(allProps);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "filter-events");
        FilterEvents mergeStreams = new FilterEvents();

        Topology topology = mergeStreams.buildTopology(properties);

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
