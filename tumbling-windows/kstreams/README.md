# Tumbling Windows in Kafka Streams

If you have time series events in a Kafka topic, tumbling windows let you group and aggregate them in fixed-size, non-overlapping, contiguous time intervals.

For example, you have a topic with events that represent movie ratings. The following topology definition counts the ratings per title over 10-minute tumbling windows.

``` java
  builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieRatingSerde))
    .map((key, rating) -> new KeyValue<>(rating.title(), rating))
    .groupByKey(Grouped.with(Serdes.String(), movieRatingSerde))
    .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(10), Duration.ofMinutes(1440)))
    .count()
    .toStream()
    .map((Windowed<String> key, Long count) -> new KeyValue<>(key.key(), count))
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()));
```

Let's review the key points in this example

``` java
    .map((key, rating) -> new KeyValue<>(rating.title(), rating))
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `map` operation which creates new key-value pairs setting the key of the stream to the `MovieRating.title` field.

``` java
    .groupByKey(Grouped.with(Serdes.String(), movieRatingSerde))
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
    .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(10), Duration.ofMinutes(1440)))
```

This creates a new `TimeWindowedKStream` that we can aggregate. The tumbling windows are 10 minutes long, and we allow data to arrive late by as much as a day.

``` java
    .count()
```

The `count()` operator is a convenience aggregation method.  Under the covers it works like any other aggregation in Kafka Streams i.e. it requires an
`Initializer`, [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) and a [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) to set the `Serde` for the value since it's a `long`.  But since the result of this aggregation is a simple count, Kafka Streams handles all those details for you.

``` java
    .toStream()
    .map((Windowed<String> key, Long count) -> new KeyValue<>(key.key(), count))
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()));
```

Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).
Then [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) converts to the expected data types.
