# Handling uncaught exceptions in Kafka Streams

You have an event streaming application, and you want to make sure that it's robust in the face of unexpected errors. Depending on the situation, you'll want the application to either continue running or shut down.  Using an implementation of the [StreamsUncaughtExceptionHandler](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/errors/StreamsUncaughtExceptionHandler.html) can provide this functionality.

To handle uncaught exceptions use the [KafkaStreams.setUncaughtExceptionHandler](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/KafkaStreams.html#setUncaughtExceptionHandler(org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler)) method:

```java
final StreamsUncaughtExceptionHandler exceptionHandler =
        new MaxFailuresUncaughtExceptionHandler(3, 3600000);

kafkaStreams.setUncaughtExceptionHandler(exceptionHandler);
```

You can also use a lambda instead of a concrete implementation:
```java
kafkaStreams.setUncaughtExceptionHander((exception) -> StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD);
```

The `StreamsUncaughtExceptionHandler` interface gives you an opportunity to respond to exceptions not handled by Kafka Streams. It has one method, `handle`, and it returns an enum of type [StreamThreadExceptionResponse](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/errors/StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.html
) which provides you the opportunity to instruct Kafka Streams how to respond to the exception. There are three possible values: `REPLACE_THREAD`, `SHUTDOWN_CLIENT`, or `SHUTDOWN_APPLICATION`.

It's important to note that the exception handler is for errors not related to malformed records as when the error occurs, Kafka Streams will not commit, and when restarting a thread, it will encounter the bad record again. 


