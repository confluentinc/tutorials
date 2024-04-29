<!-- title: How to compute the minimum or maximum value of a field with Kafka Streams -->
<!-- description: In this tutorial, learn how to compute the minimum or maximum value of a field with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to compute the minimum or maximum value of a field with Kafka Streams

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In this example
the input value is a `MovieTicketSales` object but the result is a `YearlyMovieFigures` object used to keep track of the minimum and maximum total
ticket sales by release year. You can also use windowing with aggregations to get discrete results per segment of time.


``` java
       builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieSalesSerde))
              .groupBy((k, v) -> v.releaseYear(),
                      Grouped.with(Serdes.Integer(), movieSalesSerde))
              .aggregate(() -> new YearlyMovieFigures(0, Integer.MAX_VALUE, Integer.MIN_VALUE),
                      ((key, value, aggregate) ->
                              new YearlyMovieFigures(key,
                                      Math.min(value.totalSales(), aggregate.minTotalSales()),
                                      Math.max(value.totalSales(), aggregate.maxTotalSales()))),
                      Materialized.with(Serdes.Integer(), yearlySalesSerde))
              .toStream()
              .peek((key, value) -> LOG.info("Aggregation min-max results key[{}] value[{}]", key, value))
              .to(OUTPUT_TOPIC, Produced.with(Serdes.Integer(), yearlySalesSerde));
```

Let's review the key points in this example

``` java
   .groupBy((k, v) -> v.releaseYear(),
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `groupByKey` operation on the `releaseYear` field
of the `MovieTicketSales` value object.

``` java
        .groupBy((k, v) -> v.releaseYear(), Grouped.with(Serdes.Integer(), movieSalesSerde) 
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
.aggregate(() -> new YearlyMovieFigures(0, Integer.MAX_VALUE, Integer.MIN_VALUE),
                      ((key, value, aggregate) ->
                              new YearlyMovieFigures(key,
                                      Math.min(value.totalSales(), aggregate.minTotalSales()),
                                      Math.max(value.totalSales(), aggregate.maxTotalSales()))),
                      Materialized.with(Serdes.Integer(), yearlySalesSerde))
```

This aggregation performs a running average of movie ratings.  To enable this, it keeps the running sum and count of the ratings.  The [aggregate](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Materialized-) operator takes 3 parameters (there are overloads that accept [2](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-) and [4](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Named-org.apache.kafka.streams.kstream.Materialized-) parameters):

1. An initializer for the default value in this case a new instance of the `YearlyMovieFigures` object which is a Java POJO containing current min and max sales.
2. An [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) instance which performs the aggregation action.  Here the code uses a Java [lambda expression](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html) instead of a concrete object instance.
3. A [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) object describing how the underlying [StateStore](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/processor/StateStore.html) is materialized.


``` java
 .toStream()
 .to(OUTPUT_TOPIC, Produced.with(Serdes.Integer(), yearlySalesSerde));
```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).  Then results are produced to an output topic via the `to` DSL operator.
