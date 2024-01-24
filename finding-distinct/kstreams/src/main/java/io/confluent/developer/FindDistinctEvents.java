package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class FindDistinctEvents {
    private static final Logger LOG = LoggerFactory.getLogger(FindDistinctEvents.class);
    private static final String STORE_NAME = "eventId-store";
    public static final String INPUT_TOPIC = "distinct-input-topic";
    public static final String OUTPUT_TOPIC = "distinct-output-topic";

    
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
    private static class DeduplicationProcessor<K, V, E> implements FixedKeyProcessor<K, V, V> {

        private FixedKeyProcessorContext<K, V> context;

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
        DeduplicationProcessor(final long maintainDurationPerEventInMs, final KeyValueMapper<K, V, E> idExtractor) {
            if (maintainDurationPerEventInMs < 1) {
                throw new IllegalArgumentException("maintain duration per event must be >= 1");
            }
            leftDurationMs = maintainDurationPerEventInMs / 2;
            rightDurationMs = maintainDurationPerEventInMs - leftDurationMs;
            this.idExtractor = idExtractor;
        }

        @Override
        public void init(FixedKeyProcessorContext<K, V> context) {
            this.context = context;
            eventIdStore = context.getStateStore(STORE_NAME);
        }

        @Override
        public void process(FixedKeyRecord<K, V> fixedKeyRecord) {
            K key = fixedKeyRecord.key();
            V value = fixedKeyRecord.value();
            final E eventId = idExtractor.apply(key, value);
            if (eventId == null) {
                context.forward(fixedKeyRecord);
            } else {
                final V output;
                if (isDuplicate(eventId)) {
                    output = null;
                    updateTimestampOfExistingEventToPreventExpiry(eventId, context.currentStreamTimeMs());
                } else {
                    output = value;
                    rememberNewEvent(eventId, context.currentStreamTimeMs());
                }
                context.forward(fixedKeyRecord.withValue(output));
            }
        }

        private boolean isDuplicate(final E eventId) {
            final long eventTime = context.currentStreamTimeMs();
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

    }


    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final Serde<Click> clicksSerde = StreamsSerde.serdeFor(Click.class);


        // How long we "remember" an event.  During this time, any incoming duplicates of the event
        // will be, well, dropped, thereby de-duplicating the input data.
        //
        // The actual value depends on your use case.  To reduce memory and disk usage, you could
        // decrease the size to purge old windows more frequently at the cost of potentially missing out
        // on de-duplicating late-arriving records.
        final Duration windowSize = Duration.ofMinutes(2);

        final StoreBuilder<WindowStore<String, Long>> dedupStoreBuilder = Stores.windowStoreBuilder(
                Stores.persistentWindowStore(STORE_NAME,
                        windowSize,
                        windowSize,
                        false
                ),
                Serdes.String(),
                Serdes.Long());

        builder.addStateStore(dedupStoreBuilder);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), clicksSerde))
                .peek((key, value) -> LOG.info("Incoming record key[{}] value[{}]", key, value))
                .processValues(() -> new DeduplicationProcessor<>(windowSize.toMillis(), (key, value) -> value.ip()), STORE_NAME)
                .filter((k, v) -> v != null)
                .peek((key, value) -> LOG.info("Deduplicated record key[{}] value[{}]", key, value))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), clicksSerde));

        return builder.build(allProps);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "find-distinct-events");
        FindDistinctEvents findDistinctEvents = new FindDistinctEvents();

        Topology topology = findDistinctEvents.buildTopology(properties);

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
