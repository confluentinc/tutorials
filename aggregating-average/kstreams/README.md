<!-- title: Compute an average aggregation using Kafka Streams -->
<!-- description: In this tutorial, learn how to compute an average aggregation like count or sum using Kafka Streams, with step-by-step instructions and examples. -->

# Kafka Streams Aggregation

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In our example here
the input value is a `double` but the result is a `CountAndSum` object used later to determine a running average.
You can also use windowing with aggregations to get discrete results per segments of time.

``` java annotate
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieRatingSerde))
                .map((key, value) -> KeyValue.pair(value.id(), value.rating()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                .aggregate(() -> new CountAndSum(0L, 0.0),
                        (key, value, aggregate) -> {
                            double sum = value + aggregate.sum();
                            long count = aggregate.count() + 1;
                            return new CountAndSum(count, sum);
                        },
                        Materialized.with(Serdes.String(), countAndSumSerde))
                .toStream()
                .mapValues(value -> value.sum() / value.count())
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));
```

Let's review the key points in this example

``` java
         map((key, value) -> KeyValue.pair(value.id(), value.rating())
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `map` operation which creates new key-value pairs setting the key of the stream to the `MovieRating.id` field.

``` java
        groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
.aggregate(() -> new CountAndSum(0L, 0.0),
                        (key, value, aggregate) -> {
                            double sum = value + aggregate.sum();
                            long count = aggregate.count() + 1;
                            return new CountAndSum(count, sum);
                        },
                        Materialized.with(Serdes.String(), countAndSumSerde))

```

This aggregation performs a running average of movie ratings.  To enable this, it keeps the running sum and count of the ratings.  The [aggregate](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Materialized-) operator takes 3 parameters (there are overloads that accept [2](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-) and [4](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Named-org.apache.kafka.streams.kstream.Materialized-) parameters):

1. An initializer for the default value in this case a new instance of the `CountAndSum` object which is a Java pojo containing the count and sum of scores.
2. An [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) instance which performs the aggregation action.  Here the code uses a Java [lambda expression](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html) instead of a concrete object instance.
3. A [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) object describing how the underlying [StateStore](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/processor/StateStore.html) is materialized.

``` java
                .toStream()
                .mapValues(value -> value.sum() / value.count())
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));

```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) then [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) calculates the average using the `CountAndSum` object emitted from the aggregation which is produced to an output topic. 
