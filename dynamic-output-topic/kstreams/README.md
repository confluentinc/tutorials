<!-- title: How to dynamically choose output topics with Kafka Streams -->
<!-- description: In this tutorial, learn how to dynamically choose output topics with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to dynamically choose output topics with Kafka Streams

Consider a situation where you want to direct the output of different records to different topics, like a "topic exchange." 
In this tutorial, you'll learn how to instruct Kafka Streams to choose the output topic at runtime, 
based on information in each record's header, key, or value. 

```java
  builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, orderSerde))
         .mapValues(orderProcessingSimulator)
        .to(orderTopicNameExtractor, Produced.with(stringSerde, completedOrderSerde));
```

Here's our example topology.  To dynamically route records to different topics, you'll use an instance of the [TopicNameExtractor](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/TopicNameExtractor.html).  As shown in here, you provide the `TopicNameExtractor` to 
the overloaded [KStream.to](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KStream.html#to-org.apache.kafka.streams.processor.TopicNameExtractor-org.apache.kafka.streams.kstream.Produced-).

Here's the `TopicNameExtractor` used in this example.  It uses information from the value to determine which topic Kafka Streams should use
for this record.

```java
final TopicNameExtractor<String, CompletedOrder> orderTopicNameExtractor = (key, completedOrder, recordContext) -> {
              final String compositeId = completedOrder.id();
              final String skuPart = compositeId.substring(compositeId.indexOf('-') + 1, 5);
              final String outTopic;
              if (skuPart.equals("QUA")) {
                  outTopic = SPECIAL_ORDER_OUTPUT_TOPIC;
              } else {
                  outTopic = OUTPUT_TOPIC;
              }
              return outTopic;
        };
```

The `TopicNameExtractor` interface has one method, `extract`, which makes it suitable for using a lambda, as shown here.  But remember using a concrete class has the advantage of being directly testable. 

