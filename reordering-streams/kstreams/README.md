<!-- title: How to reorder out-of-order events in Kafka Streams -->
<!-- description: In this tutorial, learn reorder out-of-order events Kafka Streams, with step-by-step instructions and supporting code. -->

# How to reorder events in Kafka Streams

Consider the case where the events in a Kafka topic are out of order.
Specifically, the producer delivered the events in order, but they are out of order from the perspective of the timestamps embedded in the event payload.

In this tutorial, we'll cover how you can reorder these records in the event stream using the embedded event timestamps.
The reordering will only occur per-partition and within a specific time window provided at startup.

NOTE: This tutorial was adapted from an [original contribution](https://github.com/confluentinc/kafka-streams-examples/pull/411) by [Sergey Shcherbakov](https://github.com/sshcherbakov)

## Setup

To accomplish the reordering, we'll leverage the fact that RocksDB stores all entries sorted by key.  
So we'll use the [KStream.process](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.7.0/org/apache/kafka/streams/kstream/KStream.html#process-org.apache.kafka.streams.processor.api.ProcessorSupplier-java.lang.String...-) method that will store incoming records into a state store using an embedded timestamp for the key. Then, we'll schedule a [punctuation](https://docs.confluent.io/platform/current/streams/developer-guide/processor-api.html#defining-a-stream-processor) to occur at a given interval that will iterate over the contents of the store and forward them to downstream operators, but now in order with respect to the embedded timestamps.
   
## Reordering by event timestamp

While the code is fairly straightforward, let's take a step-by-step walk-through of the key parts of the application. 
First we'll look at the `Processor.init` method details:

```java
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
```
 
Kafka Streams calls the`Processor.init` method when creating the topology and the method performs setup actions defined by the developer.  
In this case, the initialization steps are:
1. Store a reference to the state store used to reorder the records.
2. Store a [ProcessorContext](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.7.0/org/apache/kafka/streams/processor/api/ProcessorContext.html) reference which you'll use to forward records to downstream operators.
3. Using the `ProcessorContext` to schedule a punctuation - the main part of this tutorial.

```java
@Override
public void process(Record<K, V> kvRecord) {
    final KOrder storeKey = storeKeyGenerator.key(kvRecord.key(), kvRecord.value());
    final V storeValue = reorderStore.get(storeKey);
    
    if (storeValue == null) {
        reorderStore.put(storeKey, kvRecord.value());
    }
  }
```
Here is the `process` method, which is where the `Processor` takes action for each incoming record.
There's a `ReorderKeyGenerator` interface that takes the incoming key and value and returns the new key to order the records.  In our case, it simply returns the 
timestamp embedded in the event.  We'll discuss the `ReorderKeyGenerator` interface later in the tutorial.

Having seen how to update the key needed for sorting, now let's take a look at how Kafka Streams propagates this new order to any downstream 
operators:
```java
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
```

The `forwardOrderedByEventTime` method does the following:
1. Iterate over the current contents of the store.
2. Perform the reverse operation of the `ReorderKeyGenerator` with a `OriginalKeyExtractor` interface and provide the original key 
3. Forward each record to the next downstream operator, and then delete it.

It's critical whatever operation you use to extract the key for the sorting, you must be able to 
reverse the operation, so you can forward records with the original key. This is essential because if you do any downstream
aggregations or writing results out to a topic, the record will remain on the correct partition.

Now let's take a look at how you'll write the Kafka Steams application:

```java
StreamBuider builder = new StreamBuilder();
 builder.stream(INPUT, Consumed.with(stringSerde, eventSerde))
         .process(new ReorderingProcessorSupplier<>(reorderStore,
                        Duration.ofHours(10),
                        (k, v) -> v.eventTime(),
                        (k, v) -> v.name(),
                        Serdes.Long(),
                        eventSerde))
         .to(OUTPUT, Produced.with(stringSerde, eventSerde));
```

This is a simple Kafka Streams topology, in the `process` operator you pass in a [ProcessorSupplier]() which Kafka Streams will use to extract your `Processor` implementation. The third parameter is a lambda implementation of the `ReorderKeyGenerator` interface and the fourth is same for the `OriginalKeyExtractor` interface.  The `ReorderingProcessorSupplier` defines these two interfaces you've see before in the tutorial:

```java
public class ReorderingProcessorSupplier<KOrder, K, V> implements ProcessorSupplier<K, V, K, V> {
   // Details left out for clarity
   public interface ReorderKeyGenerator<K, V, KOrder> {
       KOrder key(K key, V val);
   }

    public interface OriginalKeyExtractor<KOrder, V, K> {
        K key(KOrder key, V val);
    }
}
```
## Important Notes 

You've seen in this tutorial how to reorder events in the stream by timestamps on the event object, but you're not limited to timestamps only -- you could use the same approach to order events in the stream by any attribute on the event.  There are a couple of points you need to keep in mind when doing so:

1. It's essential to have a way to restore the incoming key, you don't want to lose the original key-partition mapping.
2. This reordering strategy only applies to a single partition, not across multiple partitions.
3. Since an event stream is infinite, re-ordering can only be applied to distinct windows of time, and you'll balance the trade-off of large windows and iterating over the entire contents of a state store.