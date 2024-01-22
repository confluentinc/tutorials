# Sliding Windows in Kafka Streams

If you have time series events in a Kafka topic, sliding windows let you group and aggregate them in _small_ fixed-size, contiguous time intervals. Semantically,
this is the same idea as hopping windows; however, for performance reasons, hopping windows aren't the best solution for small time increments.

For example, you have a topic with events that represent temperature readings from a sensor. The following topology definition computes the average temperature for a given sensor over small 0.5-second sliding windows.

``` java
    builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), tempReadingSerde))
            .groupByKey()
            .windowedBy(SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofMillis(500), Duration.ofMillis(100)))
            .aggregate(() -> new TempAverage(0, 0),
                    (key, value, agg) -> new TempAverage(agg.total() + value.temp(), agg.num_readings() + 1),
                    Materialized.with(Serdes.String(), tempAverageSerde))
            .toStream()
            .map((Windowed<String> key, TempAverage tempAverage) -> {
                double aveNoFormat = tempAverage.total()/(double)tempAverage.num_readings();
                double formattedAve = Double.parseDouble(String.format("%.2f", aveNoFormat));
                return new KeyValue<>(key.key(),formattedAve) ;
            })
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));
```

Let's review the key points in this example

``` java
    .groupByKey()
```  

Aggregations must group records by key so grouping by key is the first step in the topology.

``` java
    .windowedBy(SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofMillis(500), Duration.ofMillis(100)))
```

This creates a new `TimeWindowedKStream` that we can aggregate. The sliding windows are 500 ms long, and we allow data to arrive late by as much as 100 ms.

``` java
    .aggregate(() -> new TempAverage(0, 0),
                    (key, value, agg) -> new TempAverage(agg.total() + value.temp(), agg.num_readings() + 1),
                    Materialized.with(Serdes.String(), tempAverageSerde))
```

Here we update the sum of temperature readings and the number of readings processed. These values are used to calculate the average temperature downstream in the topology.

``` java
    .toStream()
    .map((Windowed<String> key, TempAverage tempAverage) -> {
        double aveNoFormat = tempAverage.total()/(double)tempAverage.num_readings();
        double formattedAve = Double.parseDouble(String.format("%.2f", aveNoFormat));
        return new KeyValue<>(key.key(),formattedAve) ;
    })
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));
```

Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).
Then [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) is used to calculate the average temperature before we finally emit the aggregate to the output topic.
