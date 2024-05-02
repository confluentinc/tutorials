<!-- title: How to transform events with Kafka Streams -->
<!-- description: In this tutorial, learn how to transform events with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to transform events with Kafka Streams

If you have a stream of events in a Kafka topic and wish to transform a field in each event, you simply need to use the `KStream.map` method to process each event.

As a concrete example, consider a topic with events that represent movies. Each event has a single attribute that combines its title and its release year into a string. The following topology definition outputs these events to a new topic with title and release date turned into their own attributes.

``` java
  builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), rawMovieSerde))
    .map((key, rawMovie) -> new KeyValue<>(rawMovie.id(), convertRawMovie(rawMovie)))
    .to(OUTPUT_TOPIC, Produced.with(Serdes.Long(), movieSerde));
```

The [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) method transforms each record of the input stream into a new record in the output stream, with the movie ID serving as the key. (There is also a [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) method that can be used if you only need to transform record values.) The `convertRawMovie` method in this example splits on `::` since the attribute containing
the movie title and release year looks like `Tree of Life::2011`. The `Serde`'s included in this example use Jackson to serialize and deserialize POJOs.
