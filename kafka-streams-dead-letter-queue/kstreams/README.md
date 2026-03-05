<!-- title: Routing Events to a Dead-Letter Topic in Kafka Streams applications -->
<!-- description: In this tutorial, learn how to route events to a dead-letter topic in Kafka Streams applications, with step-by-step instructions and supporting code. -->

# Using a Dead-Letter Topic in Kafka Streams Applications

In this tutorial, you'll learn how to route events that result in exceptions to a dead-letter topic from a Kafka Streams application.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* [Apache Kafka](https://kafka.apache.org/downloads) or [Confluent Platform](https://docs.confluent.io/platform/current/installation/installing_cp/zip-tar.html) (both include the Kafka Streams application reset tool)
* Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Create Confluent Cloud resources

Login to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name kafka-streams-dlq-env \
  --kafka-cluster-name kafka-streams-dlq-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./kafka-streams-dead-letter-queue/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create input
confluent kafka topic create output
confluent kafka topic create dlq-topic
```

Start a console producer:

```shell
confluent kafka topic produce input
```

Produce events with a `ball` attribute:

```shell
{"sport": "baseball", "ball": {"shape": "round", "dimensions": {"diameter": "2.9in", "weight": "5oz"}}}
{"sport": "tennis", "ball": {"shape": "round", "dimensions": {"diameter": "6.7cm", "weight": "58g"}}}
```

And a few without a `ball` attribute:
```shell
{"sport": "swimming", "details": {"style": "backstroke", "distance": "400m"}}
{"sport": "gymnastics", "details": {"style": "floor routine"}}
```

## Configure the topology

[KIP-1034](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1034%3A+Dead+letter+queue+in+Kafka+Streams) introduced a new configuration for dead-letter queues in Kafka Streams applications. This feature provides a new configuration parameter that — when provided — instructs the default exception handler to send the erroneous event to this dead-letter queue topic. Here, the topology will just log the exception while sending an event to the dead-letter queue topic.

```java
  
  Properties properties = new Properties();

  // KIP-1034: Configure Dead Letter Queue
  properties.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLQ_TOPIC);
  properties.put(StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG, "org.apache.kafka.streams.errors.LogAndContinueProcessingExceptionHandler");

```

The example topology throws a `RuntimeException` for any events where the `ball` attribute is null or missing:

```java
  builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
          .mapValues(value -> {
              try {
                  SportEvent event = objectMapper.readValue(value, SportEvent.class);

                  if (null == event.getBall() || event.getBall().isEmpty()) {
                      LOG.error("Sport '{}' is missing ball field - routing to DLQ", event.getSport());
                      throw new RuntimeException("Sport event missing required 'ball' field");
                  }

                  LOG.info("Successfully processed event - sport: {}, ball: {}",
                          event.getSport(), event.getBall().get());
                  return value;
              } catch (IOException e) {
                  LOG.error("Failed to parse JSON value: {}", value, e);
                  throw new RuntimeException("Failed to parse JSON", e);
              }
          })
          .to(OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));
```

## Run with Confluent Cloud

Compile the application:

```shell
./gradlew kafka-streams-dead-letter-queue:kstreams:shadowJar
```

Navigate into the application directory:

```shell
cd kafka-streams-dead-letter-queue/kstreams/
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/kstreams-dlq-standalone.jar \
  io.confluent.developer.KafkaStreamsDLQApplication \
  cloud.properties
```

Observe that the two events with a `ball` attribute show up in the `output` topic:

```shell
confluent kafka topic consume output -b
```

Similarly, consume events from the `dlq-topic` and you will see that they include headers for the exception that triggered the routing, along with the partition, offset, timestamp, key, and value of the original event. For example:

```json
{
  "partition_id": 0,
  "offset": 2,
  "timestamp": 1772480434575,
  "headers": [
    {
      "key": "__streams.errors.exception",
      "value": "java.lang.RuntimeException"
    },
    {
      "key": "__streams.errors.message",
      "value": "Sport event missing required 'ball' field"
    },
    {
      "key": "__streams.errors.stacktrace",
      "value": "java.lang.RuntimeException: Sport event missing required 'ball' field\n\tat io.confluent.developer.KafkaStreamsDLQApplication.lambda$buildTopology$0(KafkaStreamsApplication.java:40)\n\tat org.apache.kafka.streams.kstream.internals.AbstractStream.lambda$withKey$0(AbstractStream.java:104)\n\tat org.apache.kafka.streams.kstream.internals.KStreamMapValues$KStreamMapProcessor.process(KStreamMapValues.java:41)\n\tat org.apache.kafka.streams.processor.internals.ProcessorNode.process(ProcessorNode.java:181)\n\tat org.apache.kafka.streams.processor.internals.ProcessorContextImpl.forwardInternal(ProcessorContextImpl.java:294)\n\tat org.apache.kafka.streams.processor.internals.ProcessorContextImpl.forward(ProcessorContextImpl.java:273)\n\tat org.apache.kafka.streams.processor.internals.ProcessorContextImpl.forward(ProcessorContextImpl.java:229)\n\tat org.apache.kafka.streams.processor.internals.SourceNode.process(SourceNode.java:95)\n\tat org.apache.kafka.streams.processor.internals.StreamTask.lambda$doProcess$0(StreamTask.java:907)\n\tat org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl.maybeMeasureLatency(StreamsMetricsImpl.java:954)\n\tat org.apache.kafka.streams.processor.internals.StreamTask.doProcess(StreamTask.java:907)\n\tat org.apache.kafka.streams.processor.internals.StreamTask.process(StreamTask.java:811)\n\tat org.apache.kafka.streams.processor.internals.TaskExecutor.processTask(TaskExecutor.java:95)\n\tat org.apache.kafka.streams.processor.internals.TaskExecutor.process(TaskExecutor.java:76)\n\tat org.apache.kafka.streams.processor.internals.TaskManager.process(TaskManager.java:2084)\n\tat org.apache.kafka.streams.processor.internals.StreamThread.runOnceWithoutProcessingThreads(StreamThread.java:1265)\n\tat org.apache.kafka.streams.processor.internals.StreamThread.runLoop(StreamThread.java:952)\n\tat org.apache.kafka.streams.processor.internals.StreamThread.run(StreamThread.java:912)\n"
    },
    {
      "key": "__streams.errors.topic",
      "value": "input"
    },
    {
      "key": "__streams.errors.partition",
      "value": "0"
    },
    {
      "key": "__streams.errors.offset",
      "value": "0"
    }
  ],
  "key": null,
  "value": {
    "sport": "swimming",
    "details": {
      "style": "backstroke",
      "distance": "400m"
    }
  },
  "metadata": {
    "value_metadata": {
      "data_format": "JSON"
    }
  }
}
```

## Clean up

When you are finished, delete the `kafka-streams-dlq-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```
