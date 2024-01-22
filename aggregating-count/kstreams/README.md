# Kafka Streams Aggregation-Count

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In our example here
we're going to use the `count()` method to perform a count on the number of tickets sold.

``` java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), ticketSaleSerde))
        .map((k, v) -> new KeyValue<>(v.title(), v.ticketTotalValue()))
        .groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
        .count()
        .toStream().mapValues(v -> v.toString() + " tickets sold")
        .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
```

Let's review the key points in this example

``` java
    map((key, value) -> new KeyValue<>(v.title(), v.ticketTotalValue()))
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `map` operation which creates new key-value pairs setting the key of the stream to the `TicketSale.title` field.

``` java
        groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
count()
```

The `count()` operator is a convenience aggregation method.  Under the covers it works like any other aggregation in Kafka Streams i.e. it requires an
`Initializer`, [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) and a [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) to set the `Serde` for the value since it's a `long`.  But since the result of this aggregation is a simple count, Kafka Streams handles all those details for you.

``` java
                .toStream().mapValues(v -> v + " tickets sold")
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) then [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) appends a string to the count to give it some context on the meaning of the number.
