package io.confluent.developer;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KTableTTL {

    public static final String INPUT_TOPIC_STREAM = "input-topic-for-stream";
    public static final String INPUT_TOPIC_TABLE = "input-topic-for-table";
    public static final String OUTPUT_TOPIC = "output-topic";

    public Topology buildTopology(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        final StreamsBuilder builder = new StreamsBuilder();

        // Read the input data.
        final KStream<String, String> stream =
                builder.stream(INPUT_TOPIC_STREAM, Consumed.with(Serdes.String(), Serdes.String()));
        final KTable<String, String> table = builder.table(INPUT_TOPIC_TABLE,
                Consumed.with(Serdes.String(), Serdes.String()));


        // Perform the custom join operation.
        final KStream<String, String> joined = stream.leftJoin(table, (left, right) -> {
            System.out.println("JOINING left=" + left + " right=" + right);
            if (right != null)
                return left + " " + right; // this is, of course, a completely fake join logic
            return left;
        });
        // Write the join results back to Kafka.
        joined.to(OUTPUT_TOPIC,
                Produced.with(Serdes.String(), Serdes.String()));


        // TTL part of the topology
        // This could be in a separate application
        // Setting tombstones for records seen past a TTL of MAX_AGE
        final Duration MAX_AGE = Duration.ofMinutes(1);
        final Duration SCAN_FREQUENCY = Duration.ofSeconds(5);
        final String STATE_STORE_NAME = "test-table-purge-store";


        // adding a custom state store for the TTL transformer which has a key of type string, and a
        // value of type long
        // which represents the timestamp
        final StoreBuilder<KeyValueStore<String, Long>> storeBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(STATE_STORE_NAME),
                Serdes.String(),
                Serdes.Long()
        );

        builder.addStateStore(storeBuilder);

        // tap the table topic in order to insert a tombstone after MAX_AGE based on event time
        table.toStream()
                .process(new TTLEmitter<String, String, String, String>(MAX_AGE,
                        SCAN_FREQUENCY, STATE_STORE_NAME), STATE_STORE_NAME)
                .to(INPUT_TOPIC_TABLE, Produced.with(Serdes.String(), Serdes.String())); // write tombstones back to input topic
        return builder.build();
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-splitting");

        KTableTTL splitStream = new KTableTTL();
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
