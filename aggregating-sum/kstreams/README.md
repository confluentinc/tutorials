# Kafka Streams Aggregation-Sum

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In our example here
we're going to use the `reduce` method to sum the total amount of tickets sold by title.

``` java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), ticketSaleSerde))
        .map((k, v) -> KeyValue.pair(v.title(), v.ticketTotalValue()))
        .groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
        .reduce(Integer::sum)
        .toStream()
        .mapValues(v -> String.format("%d total sales",v))
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
 .reduce(Integer::sum)
```

The [Reduce](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#reduce-org.apache.kafka.streams.kstream.Reducer-) operator is a special type of aggregation. A `reduce` returns the same type as the original input, in this case a sum of the current value with the previously computed value. The `reduce` method takes an instance of a [Reducer](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Reducer.html). Since a `Reducer` is a single method interface you can use method handle instead of a concrete object. In this case it's [Integer.sum](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#sum(int,int)) method that takes two integers and adds them together.

``` java
                .toStream()
                .mapValues(v -> String.format("%d total sales",v))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html)instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) then [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) appends a string to the count to give it some context on the meaning of the number.
