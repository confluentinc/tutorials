<!-- title: How to reorder out-of-order events in Kafka Streams -->
<!-- description: In this tutorial, learn reorder out-of-order events Kafka Streams, with step-by-step instructions and supporting code. -->

# How to reorder out-of-order events in Kafka Streams

In this tutorial, we take a look at the case when the order of the event time embedded in the event payload is different from the order of the timestamps applied by the Kafka Producer.  The reordering will only occur per-partition and within a specific time window provided at startup.

NOTE: This tutorial was adapted from an [original contribution](https://github.com/confluentinc/kafka-streams-examples/pull/411) by [Sergey Shcherbakov](https://github.com/sshcherbakov)

Here's the first stream of rock music:

```java
KStream<String, SongEvent> rockSongs = builder.stream(ROCK_MUSIC_INPUT, Consumed.with(stringSerde, songEventSerde));
```

And the stream of classical music:

```java
 KStream<String, SongEvent> classicalSongs = builder.stream(CLASSICAL_MUSIC_INPUT, Consumed.with(stringSerde, songEventSerde));
```

To merge these two streams you'll use the [KStream.merge](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KStream.html#merge-org.apache.kafka.streams.kstream.KStream-) operator:

```java
KStream<String, SongEvent> allSongs = rockSongs.merge(classicalSongs);
```
The `KStream.merge` method does not guarantee any order of the merged record streams.  The records maintain their ordering relative to the original source topic.
