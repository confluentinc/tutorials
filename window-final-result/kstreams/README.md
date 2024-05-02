<!-- title: How to emit only the final result of a windowed aggregation in Kafka Streams -->
<!-- description: In this tutorial, learn how to emit only the final result of a windowed aggregation in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to emit only the final result of a windowed aggregation in Kafka Streams

A windowed aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key over a specified time window. A windowed aggregation will generally return intermediate results while a window is still
open, but sometimes you may only care about the final result for a given window. In this tutorial, we take an input topic of
sensor pressure readings and output _only_ the final count of readings per sensor for each time window.

The key to emitting only final results is in the call to `KTable.suppress` in the following snippet that builds the topology:
``` java annotate
 builder.stream(INPUT_TOPIC, consumedPressure)
                .selectKey((key, value) -> value.id())
                .groupByKey(groupedPressure)
                .windowedBy(windows)
                .count()
                .suppress(Suppressed.untilWindowCloses(unbounded()))
                .toStream()
                .to(OUTPUT_TOPIC, producedCount);
```

Let's review this snippet line by line.

``` java
         .selectKey((key, value) -> value.id())
```

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `selectKey` operation specifying that the ID field in the value should be used as the key.

``` java
        .groupByKey(groupedPressure)
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
        TimeWindows windows = TimeWindows
            .ofSizeAndGrace(Duration.ofSeconds(10), Duration.ofSeconds(20))
            .advanceBy(Duration.ofSeconds(10));
```

``` java
        .windowedBy(windows)
```
This creates a windowed record stream with 10 second tumbling windows that allow a 20-second grace period during which late events may arrive.

``` java
        .count()
```

Here we count the number of records per grouped key and window using the `count` convenience method.

``` java
        .suppress(Suppressed.untilWindowCloses(unbounded()))
```

The `count` operator returns a `KTable` on which we can suppress updates until the window closes (after the 20-second grace period).


``` java
        .toStream()
        .to(OUTPUT_TOPIC, producedCount);
```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) and emitted to the output topic.
