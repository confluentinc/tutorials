# Creating your first Kafka Streams application

This tutorial demonstrates how to build a simple Kafka Streams application. You can go more in depth in the [Kafka Streams 101 course.](https://developer.confluent.io/learn-kafka/kafka-streams/get-started/)

In its simplest form, a Kafka Streams application defines a source node for consuming records from a topic, performs one or more operations or transformations on the incoming records, then produces the updated results back to Kafka.  For example, let's work through the following Kafka Streams topology definition that simply uppercases the string values from a source topic.

```java
         builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
                .mapValues(s -> s.toUpperCase())
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));
```

Let's do a quick review of this simple application.

```java
  builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
```
 
This line creates a `KStream` instance using the topic `INPUT_TOPIC` as the source and uses the `Consumed` configuration object to provide the `Serde` objects needed to deserialize the incoming records.

```java
 .mapValues(s -> s.toUpperCase())
```

Here you're performing a basic transformation on the incoming values by uppercasing each one.  
Note that with the Kafka Streams DSL you can use the fluent interface approach, chaining method calls together.

```java
.to(OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));
```
 
After the `mapValues` operation you're producing the transformed values back to Kafka. You'll see the `Produced` configuration object which provides the `Serde` objects Kafka Streams uses to serialize the records.