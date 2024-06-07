package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.*;
import org.apache.kafka.streams.state.internals.RocksDBKeyValueBytesStoreSupplier;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * User: Bill Bejeck
 * Date: 6/7/24
 * Time: 1:54 PM
 */
public class ReorderingProcessorSupplier<KOrder, K, V> implements ProcessorSupplier<K, V, K, V> {

    private final String storeName;
    private final Duration grace;
    private final ReorderKeyGenerator<K, V, KOrder> storeKeyGenerator;
    private final OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor;
    private final Serde<K> keySerde;
    private final Serde<V> valueSerde;


    public interface ReorderKeyGenerator<K, V, KOrder> {
        KOrder key(K key, V val);
    }

    public interface OriginalKeyExtractor<KOrder, V, K> {
        K key(KOrder key, V val);
    }

    public ReorderingProcessorSupplier(String storeName,
                                       Duration grace,
                                       ReorderKeyGenerator<K, V, KOrder> storeKeyGenerator,
                                       OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor,
                                       Serde<K> keySerde,
                                       Serde<V> valueSerde) {
        this.storeName = storeName;
        this.grace = grace;
        this.storeKeyGenerator = storeKeyGenerator;
        this.originalKeyExtractor = originalKeyExtractor;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public Processor<K, V, K, V> get() {
         return new ReorderProcessor(storeName, grace, storeKeyGenerator, originalKeyExtractor);
    }

    @Override
    public Set<StoreBuilder<?>> stores() {
        return Collections.singleton(Stores.keyValueStoreBuilder(new RocksDBKeyValueBytesStoreSupplier(storeName,false),keySerde, valueSerde));
    }

    private class ReorderProcessor implements Processor<K, V, K, V> {
        private final String storeName;
        private final Duration grace;
        private ProcessorContext<K, V> context;
        private KeyValueStore<KOrder, V> reorderStore;
        ReorderKeyGenerator<K, V, KOrder> storeKeyGenerator;
        OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor;

        public ReorderProcessor(String storeName,
                                Duration grace,
                                ReorderKeyGenerator<K, V, KOrder> reorderKeyGenerator,
                                OriginalKeyExtractor<KOrder, V, K> originalKeyExtractor) {

            this.storeName = storeName;
            this.grace = grace;
            this.storeKeyGenerator = reorderKeyGenerator;
            this.originalKeyExtractor = originalKeyExtractor;
        }

        @Override
        public void init(ProcessorContext<K, V> context) {
            this.reorderStore = context.getStateStore(this.storeName);
            this.context = context;
            context.schedule(
                    this.grace,
                    PunctuationType.STREAM_TIME,
                    this::punctuate
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
         * 1) read the store using a ranged fetch from 0 to timestamp - 60'000 (=1 minute)
         * 2) send the fetched messages in order using context.forward() and deletes
         * them from the store
         *
         * @param timestamp – stream time of the punctuate function call
         */
        void punctuate(final long timestamp) {
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
