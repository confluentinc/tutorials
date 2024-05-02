<!-- title: How to use the Apache Kafka &reg; console producer and consumer  -->
<!-- description: In this tutorial, learn how to use the Apache Kafka &reg; console producer and consumer. -->

# How to use the Apache Kafka &reg; console producer and consumer

So you are excited to get started with Kafka, and you'd like to produce and consume some basic messages, quickly. The console producer and consumer are also great tools to help confirm topic contents.
In this tutorial, we'll show you how to produce and consume messages from the command line without any code using the Apache Kafka console consumer and producer.

## Setup

To produce values from the command line, use this command:

```commandline
 kafka-console-producer \
  --topic <TOPIC> \
  --bootstrap-server <BOOTSTRAP-SERVER>:9092 \
  --property parse.key=true \
  --property key.separator=":"
```

By setting the `parse.key` and `key.separator` properties you can produce both a key and value in this format `my key: some text value`

To consume key-value pairs from the command line, you'll use this command for the consumer:

```commandline
kafka-console-consumer \
  --topic <TOPIC> \
  --bootstrap-server <BOOTSTRAP-SERVER>:9092 \
  --from-beginning \
  --property print.key=true \
  --property key.separator="-"
```

When the consumer starts, you'll see output in this format:  `key-value`.  If there are no keys it will look like `null-value`