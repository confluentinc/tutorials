<!-- title: How to reorder out-of-order events in Kafka Streams -->
<!-- description: In this tutorial, learn reorder out-of-order events Kafka Streams, with step-by-step instructions and supporting code. -->

# How to reorder out-of-order events in Kafka Streams

Consider the case when the order of the events in a topic is out-of-order.
To be clear, the producer delivered the events in-order, but they are out-of-order from the perspective of the timestamps embedded in the event payload.

In this tutorial, we'll cover how you can re-order these records in the event stream using the embedded event timestamps.
The reordering will only occur per-partition and within a specific time window provided at startup.

NOTE: This tutorial was adapted from an [original contribution](https://github.com/confluentinc/kafka-streams-examples/pull/411) by [Sergey Shcherbakov](https://github.com/sshcherbakov)

## Setup

To accomplish the re-ordering, we'll leverage the fact that RocksDB stores all entries sorted by key.  
So you'll use [KStream.process](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.7.0/org/apache/kafka/streams/kstream/KStream.html#process-org.apache.kafka.streams.processor.api.ProcessorSupplier-java.lang.String...-) method that will store incoming records into a state store using an embedded timestamp for the key, then schedule a [punctuation](https://docs.confluent.io/platform/current/streams/developer-guide/processor-api.html#defining-a-stream-processor) to occur at a given interval that will iterate over the contents of the store and forward them to downstream operators, but now in-order with respect to the embedded timestamps.
   
## Reordering by event timestamp

While the code is fairly straightforward, let's take a step-by-step walk through of the key parts of the application. 
First we'll look at the `Processor.init` method details:

```java
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
```
 
Kafka Streams calls the`Procerssor.init` method when creating the topology and the method performs setup actions defined by the developer.  
In this case, the initialization steps are:
1. Store a reference to the state store used to re-order the records.
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
Here is the `process` method which is where the `Processor` takes action for each incoming record.
There's a `StoreKeyGenerator` interface that takes the incoming key and value and returns a new key




