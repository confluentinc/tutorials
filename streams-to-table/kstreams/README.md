<!-- title: How to convert a `KStream` to a `KTable` in Kafka Streams -->
<!-- description: In this tutorial, learn how to convert a `KStream` to a `KTable` in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to convert a `KStream` to a `KTable` in Kafka Streams

If you have a KStream and you need to convert it to a KTable, `KStream.toTable` does the trick. Prior to the introduction of this method in Apache Kafka 2.5, a dummy aggregation operation was required.

As a concrete example, consider a topic with string keys and values. To convert the stream to a `KTable`:

``` java
  KTable<String, String> convertedTable = builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
    .toTable(Materialized.as("stream-converted-to-table"));
```
