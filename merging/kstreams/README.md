# Merging multiple streams

In this tutorial, we take a look at the case when you have multiple streams, but you'd like to merge them into one.  
We'll use multiple streams of a music catalog to demonstrate.

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
