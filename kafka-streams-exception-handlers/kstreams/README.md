<!-- title: How to handle exceptions in Kafka Streams applications -->
<!-- description: In this tutorial, learn how to handle exceptions in Kafka Streams applications, with step-by-step instructions and supporting code. -->

# How to handle exceptions in Kafka Streams applications

In this tutorial, you’ll learn how to implement and plug in Kafka Streams exception handlers to deal with errors that occur during different phases of a Kafka Streams application:

* As data enters the Kafka Streams processing topology from a Kafka topic
* During processing
* As data leaves the processing topology and is produced back to Kafka

## Prerequisites

* Java 17 or higher, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java.
* Clone the `confluentinc/tutorials` repository and navigate to its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Run a test application that triggers and handles exceptions

The `KafkaStreamsBuggyApplicationTest.testExceptionHandlers` test included in this tutorial demonstrates how exceptions are triggered and handled, as described in the sections below.

Run the test:

```shell
./gradlew kafka-streams-exception-handlers:kstreams:test
```

The handlers in this test simply log the type of exception triggered and then continue processing. You’ll see output like:

```plaintext
    ...
    ProcessingExceptionHandler triggered
    ...
    ProductionExceptionHandler.handleSerializationException triggered
    ...
    DeserializationExceptionHandler triggered
    ...
```

## Handling exceptions during deserialization

Kafka Streams applications must deserialize events as they enter the processing topology, typically using the [`Consumed.with`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/kstream/Consumed.html#with(org.apache.kafka.common.serialization.Serde,org.apache.kafka.common.serialization.Serde)) method when instantiating a `KStream`, `KTable`, or `GlobalKTable`.

To specify a built-in or custom handler for exceptions that occur during deserialization, use the [`deserialization.exception.handler`](https://kafka.apache.org/40/documentation/streams/developer-guide/config-streams#deserialization-exception-handler) configuration. The available handler implementations in Kafka are the [`LogAndContinueExceptionHandler`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/errors/LogAndContinueExceptionHandler.html) or the [`LogAndFailExceptionHandler`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/errors/LogAndFailExceptionHandler.html), or you may implement a custom handler by implementing the [`DeserializationExceptionHandler`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/errors/DeserializationExceptionHandler.html) interface. For example, the `ContinuingDeserializationExceptionHandler` in this tutorial logs a message and returns `DeserializationHandlerResponse.CONTINUE`, allowing the application to continue processing records:

```java
public class ContinuingDeserializationExceptionHandler implements DeserializationExceptionHandler {
    @Override
    public DeserializationHandlerResponse handle(final ErrorHandlerContext context,
                                                 final ConsumerRecord<byte[], byte[]> record,
                                                 final Exception exception) {
        System.out.println("DeserializationExceptionHandler triggered");
        return DeserializationHandlerResponse.CONTINUE;
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
```

### Triggering a deserialization exception

Deserialization exceptions occur when data in the input topic wasn’t serialized in a way that matches how the Kafka Streams application attempts to deserialize it.

For example, the buggy application consumes data from the input topic assuming an integer key and string value:

```java
builder.stream("input-topic", Consumed.with(Serdes.Integer(), Serdes.String()))
```

The test class triggers a deserialization exception by serializing the key as a string:

```java
// topic incorrectly serialized the key as a String, which will trigger a deserialization exception in the app
TestInputTopic<String, String> badInputTopic = driver.createInputTopic("input-topic",
        stringSerde.serializer(), stringSerde.serializer());
badInputTopic.pipeInput("1", "foo");
```

## Handling exceptions during processing

[KIP-1033](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1033%3A+Add+Kafka+Streams+exception+handler+for+exceptions+occurring+during+processing) introduced a plugin-based mechanism to handle exceptions during message processing, similar to deserialization exception handling.

To configure a built-in or custom handler, use the [`processing.exception.handler`](https://docs.confluent.io/platform/current/streams/developer-guide/config-streams.html#processing-exception-handler) configuration. The available handler implementations in Kafka are the [`LogAndContinueProcessingExceptionHandler`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/errors/LogAndContinueProcessingExceptionHandler.html) or the [`LogAndFailProcessingExceptionHandler`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/errors/LogAndFailProcessingExceptionHandler.html), or you may implement a custom handler by implementing the [`ProcessingExceptionHandler`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/errors/ProcessingExceptionHandler.html) interface. The `ContinuingProcessingExceptionHandler` in this tutorial is very similar to the `LogAndContinueProcessingExceptionHandler`. It logs a message to show you that the handler was triggered, and returns `ProcessingHandlerResponse.CONTINUE` so that the application will continue processing records:

```java
public class ContinuingProcessingExceptionHandler implements ProcessingExceptionHandler {
  @Override
  public ProcessingHandlerResponse handle(final ErrorHandlerContext context,
                                          final Record<?, ?> record,
                                          final Exception exception) {
    System.out.println("ProcessingExceptionHandler triggered");
    return ProcessingHandlerResponse.CONTINUE;
  }

  @Override
  public void configure(Map<String, ?> configs) {
  }
}
```

### Triggering a processing exception

One way to trigger a processing exception is to throw an exception from a `Processor` that is invoked by calling [`KStream.process`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/kstream/KStream.html#process(org.apache.kafka.streams.processor.api.ProcessorSupplier,java.lang.String...)). This tutorial's application includes a `Processor` that fails randomly and otherwise is a no-op that forwards the record:

```java
@Override
public void process(Record record) {
    if (Math.random() < 0.5) {
        throw new RuntimeException("fail!!");
    }
    context.forward(record);
}
```

## Handling exceptions when producing back to Kafka

[KIP-210](https://cwiki.apache.org/confluence/display/KAFKA/KIP-210+-+Provide+for+custom+error+handling++when+Kafka+Streams+fails+to+produce) introduced a mechanism for handling exceptions that occur while producing records back to Kafka. This was extended in [KIP-399](https://cwiki.apache.org/confluence/display/KAFKA/KIP-399%3A+Extend+ProductionExceptionHandler+to+cover+serialization+exceptions) to include serialization exceptions.

To configure a handler, use the [`production.exception.handler`](https://kafka.apache.org/40/documentation/streams/developer-guide/config-streams#production-exception-handler) configuration. The class must implement the [`ProductionExceptionHandler`](https://docs.confluent.io/platform/current/streams/javadocs/javadoc/org/apache/kafka/streams/errors/ProductionExceptionHandler.html) interface. Here’s an example from this tutorial:

```java
public class ContinuingProductionExceptionHandler implements ProductionExceptionHandler {
  @Override
  public ProductionExceptionHandlerResponse handle(final ErrorHandlerContext context,
                                                   final ProducerRecord<byte[], byte[]> record,
                                                   final Exception exception) {
    System.out.println("ProductionExceptionHandler.handle triggered");
    return ProductionExceptionHandlerResponse.CONTINUE;
  }

  @Override
  public ProductionExceptionHandlerResponse handleSerializationException(final ErrorHandlerContext context,
                                                                         final ProducerRecord record,
                                                                         final Exception exception,
                                                                         final SerializationExceptionOrigin origin) {
    System.out.println("ProductionExceptionHandler.handleSerializationException triggered");
    return ProductionExceptionHandlerResponse.CONTINUE;
  }

  @Override
  public void configure(Map<String, ?> configs) {
  }
}
```

### Triggering a production exception

Production exceptions can occur for various reasons, including:

* Security compliance (e.g., authentication or authorization errors)
* Misconfiguration (e.g., `InvalidTopicException`)
* Networking issues (e.g., `UnknownServerException`)

In this tutorial, a production exception is triggered using a custom serializer that randomly fails:

```java
Serde<String> randomlyFailingStringSerde = Serdes.serdeFrom(new RandomlyFailingSerializer(), Serdes.String().deserializer());

builder.stream("input-topic", Consumed.with(Serdes.Integer(), Serdes.String()))
        ...
        .to("output-topic", Produced.with(Serdes.Integer(), randomlyFailingStringSerde));
```
