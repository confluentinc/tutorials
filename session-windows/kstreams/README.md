# Session Windows in Kafka Streams

If you have time series events in a Kafka topic, session windows let you group and aggregate them into variable-size, non-overlapping time intervals based on a configurable inactivity period.

For example, suppose that you have a topic with events that represent website clicks. The following topology definition counts the number of clicks per source IP address for windows that close after 5 minutes of inactivity.
``` java
    builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), clickSerde))
            .groupByKey()
            .windowedBy(SessionWindows.ofInactivityGapAndGrace(Duration.ofMinutes(5), Duration.ofSeconds(30)))
            .count()
            .toStream()
            .map((windowedKey, count) ->  {
                String start = timeFormatter.format(windowedKey.window().startTime());
                String end = timeFormatter.format(windowedKey.window().endTime());
                String sessionInfo = String.format("Session info started: %s ended: %s with count %s", start, end, count);
                return KeyValue.pair(windowedKey.key(), sessionInfo);
            })
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
```

Let's review the key points in this example.

``` java
    .groupByKey()
```

Aggregations must group records by key. By not passing an argument, we use the current key (the source IP address).

``` java
    .windowedBy(SessionWindows.ofInactivityGapAndGrace(Duration.ofMinutes(5), Duration.ofSeconds(30)))
```

This creates a new `SessionWindowedKStream` over which we can aggregate. The session windows close after 5 minutes of inactivity, and we allow data to arrive late by as much as 30 seconds.

``` java
    .count()
```

The `count()` operator is a convenience aggregation method.  Under the covers it works like any other aggregation in Kafka Streams — i.e., it requires an
`Initializer`, [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) and a [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) to set the `Serde` for the value since it's a `long`.  But, since the result of this aggregation is a simple count, Kafka Streams handles those details for you.

``` java
    .toStream()
    .map((windowedKey, count) ->  {
        String start = timeFormatter.format(windowedKey.window().startTime());
        String end = timeFormatter.format(windowedKey.window().endTime());
        String sessionInfo = String.format("Session info started: %s ended: %s with count %s", start, end, count);
        return KeyValue.pair(windowedKey.key(), sessionInfo);
    })
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
```

Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).
Then [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) converts to the expected data types. The value is a formatted String containing session information.
