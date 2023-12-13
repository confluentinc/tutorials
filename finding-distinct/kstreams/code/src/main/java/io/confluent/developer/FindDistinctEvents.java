package io.confluent.developer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.common.utils.TestUtils;
import io.confluent.developer.avro.Click;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

public class FindDistinctEvents {

    private static final String storeName = "eventId-store";

    /**
     * Discards duplicate click events from the input stream by ip address
     * <p>
     * Duplicate records are detected based on ip address
     * The transformer remembers known ip addresses within an associated window state
     * store, which automatically purges/expires IPs from the store after a certain amount of
     * time has passed to prevent the store from growing indefinitely.
     * <p>
     * Note: This code is for demonstration purposes and was not tested for production usage.
     */
    private static class DeduplicationTransformer<K, V, E> implements ValueTransformerWithKey<K, V, V> {

        private ProcessorContext context;

        /**
         * Key: ip address
         * Value: timestamp (event-time) of the corresponding event when the event ID was seen for the
         * first time
         */
        private WindowStore<E, Long> eventIdStore;

        private final long leftDurationMs;
        private final long rightDurationMs;

        private final KeyValueMapper<K, V, E> idExtractor;

        /**
         * @param maintainDurationPerEventInMs how long to "remember" a known ip address
         *                                     during the time of which any incoming duplicates
         *                                     will be dropped, thereby de-duplicating the
         *                                     input.
         * @param idExtractor                  extracts a unique identifier from a record by which we de-duplicate input
         *                                     records; if it returns null, the record will not be considered for
         *                                     de-duping but forwarded as-is.
         */
        DeduplicationTransformer(final long maintainDurationPerEventInMs, final KeyValueMapper<K, V, E> idExtractor) {
            if (maintainDurationPerEventInMs < 1) {
                throw new IllegalArgumentException("maintain duration per event must be >= 1");
            }
            leftDurationMs = maintainDurationPerEventInMs / 2;
            rightDurationMs = maintainDurationPerEventInMs - leftDurationMs;
            this.idExtractor = idExtractor;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void init(final ProcessorContext context) {
            this.context = context;
            eventIdStore = (WindowStore<E, Long>) context.getStateStore(storeName);
        }

        @Override
        public V transform(final K key, final V value) {
            final E eventId = idExtractor.apply(key, value);
            if (eventId == null) {
                return value;
            } else {
                final V output;
                if (isDuplicate(eventId)) {
                    output = null;
                    updateTimestampOfExistingEventToPreventExpiry(eventId, context.timestamp());
                } else {
                    output = value;
                    rememberNewEvent(eventId, context.timestamp());
                }
                return output;
            }
        }

        private boolean isDuplicate(final E eventId) {
            final long eventTime = context.timestamp();
            final WindowStoreIterator<Long> timeIterator = eventIdStore.fetch(
                    eventId,
                    eventTime - leftDurationMs,
                    eventTime + rightDurationMs);
            final boolean isDuplicate = timeIterator.hasNext();
            timeIterator.close();
            return isDuplicate;
        }

        private void updateTimestampOfExistingEventToPreventExpiry(final E eventId, final long newTimestamp) {
            eventIdStore.put(eventId, newTimestamp, newTimestamp);
        }

        private void rememberNewEvent(final E eventId, final long timestamp) {
            eventIdStore.put(eventId, timestamp, timestamp);
        }

        @Override
        public void close() {
            // Note: The store should NOT be closed manually here via `eventIdStore.close()`!
            // The Kafka Streams API will automatically close stores when necessary.
        }

    }

    private SpecificAvroSerde<Click> buildClicksSerde(final Properties allProps) {
        final SpecificAvroSerde<Click> serde = new SpecificAvroSerde<>();
        final Map<String, String> config = (Map)allProps;
        serde.configure(config, false);
        return serde;
    }

    public Topology buildTopology(Properties allProps,
                                  final SpecificAvroSerde<Click> clicksSerde) {
        final StreamsBuilder builder = new StreamsBuilder();

        final String inputTopic = allProps.getProperty("input.topic.name");
        final String outputTopic = allProps.getProperty("output.topic.name");

        // How long we "remember" an event.  During this time, any incoming duplicates of the event
        // will be, well, dropped, thereby de-duplicating the input data.
        //
        // The actual value depends on your use case.  To reduce memory and disk usage, you could
        // decrease the size to purge old windows more frequently at the cost of potentially missing out
        // on de-duplicating late-arriving records.
        final Duration windowSize = Duration.ofMinutes(2);

        // retention period must be at least window size -- for this use case, we don't need a longer retention period
        // and thus just use the window size as retention time
        final Duration retentionPeriod = windowSize;

        final StoreBuilder<WindowStore<String, Long>> dedupStoreBuilder = Stores.windowStoreBuilder(
                Stores.persistentWindowStore(storeName,
                        retentionPeriod,
                        windowSize,
                        false
                ),
                Serdes.String(),
                Serdes.Long());

        builder.addStateStore(dedupStoreBuilder);

        builder
                .stream(inputTopic, Consumed.with(Serdes.String(), clicksSerde))
                .transformValues(() -> new DeduplicationTransformer<>(windowSize.toMillis(), (key, value) -> value.getIp()), storeName)
                .filter((k, v) -> v != null)
                .to(outputTopic, Produced.with(Serdes.String(), clicksSerde));

        return builder.build();
    }

    public void createTopics(Properties allProps) {
        AdminClient client = AdminClient.create(allProps);

        List<NewTopic> topics = new ArrayList<>();
        topics.add(new NewTopic(
                allProps.getProperty("input.topic.name"),
                Integer.parseInt(allProps.getProperty("input.topic.partitions")),
                Short.parseShort(allProps.getProperty("input.topic.replication.factor"))));
        topics.add(new NewTopic(
                allProps.getProperty("output.topic.name"),
                Integer.parseInt(allProps.getProperty("output.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.topic.replication.factor"))));

        client.createTopics(topics);
        client.close();
    }

    public static Properties loadEnvProperties(String fileName) throws IOException {
        Properties allProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        allProps.load(input);
        input.close();

        return allProps;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "This program takes one argument: the path to an environment configuration file.");
        }

        new FindDistinctEvents().runRecipe(args[0]);
    }

    private void runRecipe(final String configPath) throws IOException {
        final Properties allProps = new Properties();
        try (InputStream inputStream = new FileInputStream(configPath)) {
            allProps.load(inputStream);
        }
        allProps.put(StreamsConfig.APPLICATION_ID_CONFIG, allProps.getProperty("application.id"));
        allProps.put(StreamsConfig.STATE_DIR_CONFIG, TestUtils.tempDirectory().getPath());

        final Topology topology = this.buildTopology(allProps, this.buildClicksSerde(allProps));

        this.createTopics(allProps);

        final KafkaStreams streams = new KafkaStreams(topology, allProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close(Duration.ofSeconds(5));
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);

    }
}
