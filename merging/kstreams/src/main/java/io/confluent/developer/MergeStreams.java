package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class MergeStreams {
         private static final Logger LOG = LoggerFactory.getLogger(MergeStreams.class);
         public static final String ROCK_MUSIC_INPUT = "rock-input";
         public static final String CLASSICAL_MUSIC_INPUT = "classical-input";
         public static final String ALL_MUSIC_OUTPUT = "all-music-output";
    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        Serde<String> stringSerde = Serdes.String();
        Serde<SongEvent> songEventSerde = StreamsSerde.serdeFor(SongEvent.class);

        KStream<String, SongEvent> rockSongs = builder.stream(ROCK_MUSIC_INPUT, Consumed.with(stringSerde, songEventSerde))
                .peek((key, value) -> LOG.info("Incoming rock music key[{}] value[{}]", key, value));

        KStream<String, SongEvent> classicalSongs = builder.stream(CLASSICAL_MUSIC_INPUT, Consumed.with(stringSerde, songEventSerde))
                .peek((key, value) -> LOG.info("Incoming classical key[{}] value[{}]", key, value));

        KStream<String, SongEvent> allSongs = rockSongs.merge(classicalSongs);
        allSongs.peek((key, value) -> LOG.info("Merged stream outgoing key[{}] value[{}]", key, value))
                .to(ALL_MUSIC_OUTPUT, Produced.with(stringSerde, songEventSerde));
        
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
        MergeStreams mergeStreams = new MergeStreams();

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
