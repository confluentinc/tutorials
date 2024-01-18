package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class SplitStream {

    private static final Logger LOG = LoggerFactory.getLogger(SplitStream.class);
    public static final String INPUT_TOPIC = "acting-events";
    public static final String OUTPUT_TOPIC_DRAMA = "acting-events-drama";
    public static final String OUTPUT_TOPIC_FANTASY = "acting-events-fantasy";
    public static final String OUTPUT_TOPIC_OTHER = "acting-events-other";

    public Topology buildTopology(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        final StreamsBuilder builder = new StreamsBuilder();

        Serde<ActingEvent> actingEventSerde = StreamsSerde.serdeFor(ActingEvent.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), actingEventSerde))
                .split()
                .branch((key, appearance) -> "drama".equals(appearance.genre()),
                        Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_DRAMA)))
                .branch((key, appearance) -> "fantasy".equals(appearance.genre()),
                        Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_FANTASY)))
                .branch((key, appearance) -> true,
                        Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_OTHER)));

        return builder.build(properties);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-splitting");

        SplitStream splitStream = new SplitStream();
        Topology topology = splitStream.buildTopology(properties);

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
