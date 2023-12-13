package io.confluent.developer;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * A simple transformer maintaining a purge store of keys and the
 * last update time and if a TTL has been exceeded, emits tombstones
 * for those keys
 *
 * @author sarwar
 *
 * @param <K>
 * @param <V>
 * @param <R>
 */
public class TTLKTableTombstoneEmitter<K, V, R> implements Transformer<K, V, R> {

  private final Duration maxAge;
  private final Duration scanFrequency;
  private final String purgeStoreName;
  private ProcessorContext context;
  private KeyValueStore<K, Long> stateStore;


  public TTLKTableTombstoneEmitter(final Duration maxAge, final Duration scanFrequency,
      final String stateStoreName) {
    this.maxAge = maxAge;
    this.scanFrequency = scanFrequency;
    this.purgeStoreName = stateStoreName;
  }

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
    this.stateStore = (KeyValueStore<K, Long>) context.getStateStore(purgeStoreName);
    // This is where the delete signal is created. This HAS to use Wallcock Time
    // as the we may not updates to the table's input topic
    context.schedule(scanFrequency, PunctuationType.WALL_CLOCK_TIME, timestamp -> {
      final long cutoff = timestamp - maxAge.toMillis();

      // scan over all the keys in this partition's store
      // this can be optimized, but just keeping it simple.
      // this might take a while, so the Streams timeouts should take this into account
      try (final KeyValueIterator<K, Long> all = stateStore.all()) {
        while (all.hasNext()) {
          final KeyValue<K, Long> record = all.next();
          System.out.println("RECORD "+record.key+":"+record.value);

          if (record.value != null && record.value < cutoff) {

            System.out.println("Forwarding Null for key "+record.key);
            // if a record's last update was older than our cutoff, emit a tombstone.
            ValueWrapper vw = new ValueWrapper();
            vw.setDeleted(true);
            context.forward(record.key, vw);
            stateStore.delete(record.key);
          }
        }
      }
    });
  }

  @Override
  public R transform(K key, V value) {

    // this gets invoked for each new record we consume. If it's a tombstone, delete
    // it from our state store. Otherwise, store the record timestamp.
    if (value == null) {
      System.out.println("CLEARING key="+key);
      stateStore.delete(key);
    } else {
      System.out.println("UPDATING key="+key);
      stateStore.put(key, context.timestamp());
    }
    return (R)(new KeyValue<K,ValueWrapper>(key,new ValueWrapper(value)));
  }

  @Override
  public void close() {


  }

}
