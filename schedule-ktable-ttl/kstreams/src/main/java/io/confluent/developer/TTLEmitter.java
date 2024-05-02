package io.confluent.developer;

import java.time.Duration;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * A simple transformer maintaining a purge store of keys and the
 * last update time and if a TTL has been exceeded, emits tombstones
 * for those keys
 */
public class TTLEmitter<Kin, Vin, Kout, Vout> implements ProcessorSupplier<Kin, Vin, Kout, Vout> {

    private final Duration maxAge;
    private final Duration scanFrequency;
    private final String purgeStoreName;
    private ProcessorContext context;

    public TTLEmitter(final Duration maxAge, final Duration scanFrequency,
                      final String stateStoreName) {
        this.maxAge = maxAge;
        this.scanFrequency = scanFrequency;
        this.purgeStoreName = stateStoreName;
    }


    @Override
    public Processor<Kin, Vin, Kout, Vout> get() {
        return new Processor<>() {
            @Override
            public void close() {
                Processor.super.close();
            }

            private ProcessorContext<Kout, Vout> context;
            private KeyValueStore<Kout, Long> stateStore;


            @Override
            public void init(ProcessorContext<Kout, Vout> context) {
                this.context = context;
                this.stateStore = context.getStateStore(purgeStoreName);
                // This is where the magic happens. This causes Streams to invoke the Punctuator
                // on an interval, using stream time. That is, time is only advanced by the record
                // timestamps
                // that Streams observes. This has several advantages over wall-clock time for this
                // application:
                //
                // It'll produce the exact same sequence of updates given the same sequence of data.
                // This seems nice, since the purpose is to modify the data stream itself, you want to
                // have a clear understanding of when stuff is going to get deleted. For example, if something
                // breaks down upstream for this topic, and it stops getting new data for a while, wall
                // clock time would just keep deleting data on schedule, whereas stream time will wait for
                // new updates to come in.
                //
                // You can change to wall clock time here if that is what is needed
                context.schedule(scanFrequency, PunctuationType.STREAM_TIME, timestamp -> {
                    final long cutoff = timestamp - maxAge.toMillis();

                    // scan over all the keys in this partition's store
                    // this can be optimized, but just keeping it simple.
                    // this might take a while, so the Streams timeouts should take this into account
                    try (final KeyValueIterator<Kout, Long> all = stateStore.all()) {
                        while (all.hasNext()) {
                            final KeyValue<Kout, Long> record = all.next();
                            if (record.value != null && record.value < cutoff) {
                                System.out.println("Forwarding Null");
                                // if a record's last update was older than our cutoff, emit a tombstone.
                                context.forward(new Record(record.key, null, 0, null));
                            }
                        }
                    }
                });
            }

            @Override
            public void process(Record<Kin, Vin> record) {
                // this gets invoked for each new record we consume. If it's a tombstone, delete
                // it from our state store. Otherwise, store the record timestamp.
                if (record.value() == null) {
                    System.out.println("CLEANING key=" + record.key());
                    stateStore.delete((Kout) record.key());
                } else {
                    System.out.println("UPDATING key=" + record.key());
                    stateStore.put((Kout) record.key(), context.currentStreamTimeMs());
                }
            }
        };
    }

}
