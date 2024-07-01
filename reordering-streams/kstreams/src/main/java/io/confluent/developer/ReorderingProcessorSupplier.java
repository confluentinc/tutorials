package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class ReorderingProcessorSupplier<KOrder, K, V> implements ProcessorSupplier<K, V, K, V> {
    private final String storeName;
    private final Duration reorderWindow;
    private final ReorderKeyGenerator<K, V, KOrder> storeKeyGenerator;
    private final OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor;
    private final Serde<KOrder> keySerde;
    private final Serde<V> valueSerde;


    public interface ReorderKeyGenerator<K, V, KOrder> {
        KOrder key(K key, V val);
    }

    public interface OriginalKeyExtractor<KOrder, V, K> {
        K key(KOrder key, V val);
    }

    public ReorderingProcessorSupplier(String storeName,
                                       Duration reorderWindow,
                                       ReorderKeyGenerator<K, V, KOrder> storeKeyGenerator,
                                       OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor,
                                       Serde<KOrder> keySerde,
                                       Serde<V> valueSerde) {
        this.storeName = storeName;
        this.reorderWindow = reorderWindow;
        this.storeKeyGenerator = storeKeyGenerator;
        this.originalKeyExtractor = originalKeyExtractor;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public Processor<K, V, K, V> get() {
         return new ReorderProcessor(storeName, reorderWindow, storeKeyGenerator, originalKeyExtractor);
    }

    @Override
    public Set<StoreBuilder<?>> stores() {
        return Collections.singleton(Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(storeName),keySerde, valueSerde));
    }

    private class ReorderProcessor implements Processor<K, V, K, V> {
        private final String storeName;
        private final Duration reorderWindow;
        private ProcessorContext<K, V> context;
        private KeyValueStore<KOrder, V> reorderStore;
        private final ReorderKeyGenerator<K, V, KOrder> storeKeyGenerator;
        private final OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor;

        public ReorderProcessor(String storeName,
                                Duration reorderWindow,
                                ReorderKeyGenerator<K, V, KOrder> reorderKeyGenerator,
                                OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor) {

            this.storeName = storeName;
            this.reorderWindow = reorderWindow;
            this.storeKeyGenerator = reorderKeyGenerator;
            this.originalKeyExtractor = originalKeyExtractor;
        }

        @Override
        public void init(ProcessorContext<K, V> context) {
            this.reorderStore = context.getStateStore(this.storeName);
            this.context = context;
            context.schedule(
                    this.reorderWindow,
                    PunctuationType.STREAM_TIME,
                    this::forwardOrderedByEventTime
            );
        }

        @Override
        public void process(Record<K, V> kvRecord) {
            final KOrder storeKey = storeKeyGenerator.key(kvRecord.key(), kvRecord.value());
            final V storeValue = reorderStore.get(storeKey);
            if (storeValue == null) {
                reorderStore.put(storeKey, kvRecord.value());
            }
        }


        /**
         * Scheduled to be called automatically when the period
         * within which message reordering occurs expires.
         * <p>
         * Outputs downstream accumulated records sorted by their timestamp.
         * <p>
         * 1) read the store
         * 2) send the fetched messages in order using context.forward() and deletes
         * them from the store
         *
         * @param timestamp – stream time of the punctuate function call
         */
        void forwardOrderedByEventTime(final long timestamp) {
            try (KeyValueIterator<KOrder, V> it = reorderStore.all()) {
                while (it.hasNext()) {
                    final KeyValue<KOrder, V> kv = it.next();
                    K origKey = originalKeyExtractor.key(kv.key, kv.value);
                    context.forward(new Record<>(origKey, kv.value, timestamp));
                    reorderStore.delete(kv.key);
                }
            }
        }
    }
}
