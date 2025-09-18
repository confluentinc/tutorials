package io.confluent.developer;


import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.VersionedBytesStoreSupplier;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class VersionedKTableExample {

    public static final String STREAM_INPUT_TOPIC = "stream-input-topic";
    public static final String TABLE_INPUT_TOPIC = "table-input-topic";
    public static final String OUTPUT_TOPIC = "output-topic";


    public Topology buildTopology() {
        final StreamsBuilder builder = new StreamsBuilder();

        final Serde<String> stringSerde = Serdes.String();

        final VersionedBytesStoreSupplier versionedStoreSupplier = Stores.persistentVersionedKeyValueStore("versioned-ktable-store", Duration.ofMinutes(10));

        final KStream<String, String> streamInput = builder.stream(STREAM_INPUT_TOPIC, Consumed.with(stringSerde, stringSerde));

        final KTable<String, String> tableInput = builder.table(TABLE_INPUT_TOPIC,
                Materialized.<String, String>as(versionedStoreSupplier)
                        .withKeySerde(stringSerde)
                        .withValueSerde(stringSerde));
        final ValueJoiner<String, String, String> valueJoiner = (val1, val2) -> val1 + " " + val2;

        streamInput.join(tableInput, valueJoiner)
                .peek((key, value) -> System.out.println("Joined value: " + value))
                .to(OUTPUT_TOPIC,
                        Produced.with(stringSerde, stringSerde));

        return builder.build();
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "versioned-ktable-application");

        VersionedKTableExample versionedKTable = new VersionedKTableExample();
        Topology topology = versionedKTable.buildTopology();

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
