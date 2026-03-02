<!-- title: Routing Events to a Dead-Letter Topic in Kafka Streams applications -->
<!-- description: In this tutorial, learn how to route events to a dead-letter topic in Kafka Streams applications, with step-by-step instructions and supporting code. -->

# Using a Dead-Letter Topic in Kafka Streams Applications

In this tutorial, you'll learn how to route events that result in exceptions to a dead-letter topic from a Kafka Streams application.

## Prerequisites

* Java 17 or higher, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java.
* Clone the `confluentinc/tutorials` repository and navigate to its top-level directory:

  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Configure the topology

[KIP-1034](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1034%3A+Dead+letter+queue+in+Kafka+Streams) introduced a new configuration for dead-letter queues in Kafka Streams applications. This feature provides a new configuration parameter that - when provided - instructs the default exception handler to send the erroneous event to this dead-letter queue topic. Here, the topology will just log the exception while sending an event to the dead-letter queue topic.

```java
  
  Properties properties = new Properties();

  // KIP-1034: Configure Dead Letter Queue
  properties.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLQ_TOPIC);
  properties.put(StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG, "org.apache.kafka.streams.errors.LogAndContinueProcessingExceptionHandler");

```

## Input events

In this example, events contain different sports. Some sports involve a `ball` and others do not.

For example, an event for the sports of `baseball` and `tennis` might look like this:

```json
{"sport": "baseball", "ball": {"shape": "round", "dimensions": {"diameter": "2.9in", "weight": "5oz"}}}
{"sport": "tennis", "ball": {"shape": "round", "dimensions": {"diameter": "6.7cm", "weight": "58g"}}}
```

While `swimming` would look like this:

```json
{"sport": "swimming", "details": {"style": "backstroke", "distance": "400m"}}
```

## The topology

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

## Run the unit and integration tests

The unit test `KafkaStreamsApplicationTest` contains scenarios for `sport` events where the `ball` is both provided and missing. However that unit test does not assert for events in the dead-letter queue because the `TopologyTestDriver` does not simulate this routing. This test does assert that the events without a `ball` are not sent to the `output` topic.


The integration test `KafkaStreamsApplicationDLQIntegrationTest` does assert on the dead-letter queue using a simple `KafkaConsumer` to subscribe to the `dlq-topic`.

## In the dead-leter queue topic

Events in the `dlq-topic` include headers for the exception that triggered the routing, along with the partition, offset, timestamp, key, and value of the original event. For example:

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
      "value": "java.lang.RuntimeException: Sport event missing required 'ball' field\n\tat io.confluent.developer.KafkaStreamsApplication.lambda$buildTopology$0(KafkaStreamsApplication.java:40)\n\tat org.apache.kafka.streams.kstream.internals.AbstractStream.lambda$withKey$0(AbstractStream.java:104)\n\tat org.apache.kafka.streams.kstream.internals.KStreamMapValues$KStreamMapProcessor.process(KStreamMapValues.java:41)\n\tat org.apache.kafka.streams.processor.internals.ProcessorNode.process(ProcessorNode.java:181)\n\tat org.apache.kafka.streams.processor.internals.ProcessorContextImpl.forwardInternal(ProcessorContextImpl.java:294)\n\tat org.apache.kafka.streams.processor.internals.ProcessorContextImpl.forward(ProcessorContextImpl.java:273)\n\tat org.apache.kafka.streams.processor.internals.ProcessorContextImpl.forward(ProcessorContextImpl.java:229)\n\tat org.apache.kafka.streams.processor.internals.SourceNode.process(SourceNode.java:95)\n\tat org.apache.kafka.streams.processor.internals.StreamTask.lambda$doProcess$0(StreamTask.java:907)\n\tat org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl.maybeMeasureLatency(StreamsMetricsImpl.java:954)\n\tat org.apache.kafka.streams.processor.internals.StreamTask.doProcess(StreamTask.java:907)\n\tat org.apache.kafka.streams.processor.internals.StreamTask.process(StreamTask.java:811)\n\tat org.apache.kafka.streams.processor.internals.TaskExecutor.processTask(TaskExecutor.java:95)\n\tat org.apache.kafka.streams.processor.internals.TaskExecutor.process(TaskExecutor.java:76)\n\tat org.apache.kafka.streams.processor.internals.TaskManager.process(TaskManager.java:2084)\n\tat org.apache.kafka.streams.processor.internals.StreamThread.runOnceWithoutProcessingThreads(StreamThread.java:1265)\n\tat org.apache.kafka.streams.processor.internals.StreamThread.runLoop(StreamThread.java:952)\n\tat org.apache.kafka.streams.processor.internals.StreamThread.run(StreamThread.java:912)\n"
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
