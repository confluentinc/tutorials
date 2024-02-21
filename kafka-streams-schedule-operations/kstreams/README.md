# Scheduling operations

You'd like to have some periodic functionality execute in your Kafka Streams application. In this tutorial, you'll learn how to use punctuations in Kafka Streams to execute work at regular intervals.

To schedule operations in Kafka Streams, you'll need to use the [Processor API](https://kafka.apache.org/36/documentation/streams/developer-guide/processor-api.html).  In this example, you will use the [KStream.process](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KStream.html#process-org.apache.kafka.streams.processor.api.ProcessorSupplier-org.apache.kafka.streams.kstream.Named-java.lang.String...-) method which mixes the Processor API into the Kafka Streams DSL.

```java
 final KStream<String, LoginTime> loginTimeStream = builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), loginTimeSerde));

        loginTimeStream
                .process(new PunctationProcessorSupplier(), Named.as("max-login-time-transformer"), LOGIN_TIME_STORE)
                      .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()));
```

When you call the `KStream.process` method, you'll pass in a [ProcessorSupplier](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/ProcessorSupplier.html), which returns your [Processor](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/Processor.html) implementation.

It's in the `Process.init` method where you'll [schedule](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/ProcessingContext.html#schedule-java.time.Duration-org.apache.kafka.streams.processor.PunctuationType-org.apache.kafka.streams.processor.Punctuator-) arbitrary actions with the [ProcessorContext](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/ProcessorContext.html)

```java
 public void init(ProcessorContext<String, Long> context) {
    this.context = context;
    this.context.schedule(Duration.ofSeconds(5), PunctuationType.STREAM_TIME, this::streamTimePunctuator);
    this.context.schedule(Duration.ofSeconds(20), PunctuationType.WALL_CLOCK_TIME, this::wallClockTimePunctuator);
}
```
Here you're scheduling two actions, one using stream time and another on wall-clock time.  With stream time, the event timestamps of the incoming records advance
the internal time tracked by Kafka Streams.  Wall-clock time is the system time of the machine running the Kafka Streams application.  Kafka Streams measures wall-clock during its `poll` interval so executing punctuation based on wall-clock time is best effort only. Here the code uses method handles instead of concrete [Punctuator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/Punctuator.html) instances.


