# Confluent parallel consumer
The Confluent Parallel Consumer is an open-source Apache 2.0-licensed Java library that enables you to consume from a Kafka topic with more parallelism than the number of partitions.  In an Apache Kafka consumer group, the number of partitions is the parallelism limit.
Increasing the level of parallelism beyond the partition count is desirable in many situations.  For example, when there are fixed partition counts for a reason beyond your control or if you need to make a high-latency call out to a database or microservice while consuming and want to increase throughput.

In this tutorial, you'll build a small "hello world" application that uses the Confluent Parallel Consumer library.  There are also some performance tests at a larger scale to compare the Confluent Parallel Consumer with a baseline built using a vanilla Apache Kafka consumer group you can explore on your own.

## ParallelStreamProcessor

For parallel record consuming, you'll use the [ParallelStreamProcessor](https://javadoc.io/doc/io.confluent.parallelconsumer/parallel-consumer-core/latest/io/confluent/parallelconsumer/ParallelStreamProcessor.html) which wraps a [KafkaConsumer](https://kafka.apache.org/36/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html).

You create a new instance of a `KafkaConsumer`, create a `ParallelConsumerOptions` configuration object, then use the configuration to create a new `ParallelStreamProcessor` instance:

```java
final Consumer<String, String> consumer = new KafkaConsumer<>(appProperties);  
    final ParallelConsumerOptions options = ParallelConsumerOptions.<String, String>builder()  
        .ordering(KEY)  
        .maxConcurrency(16)  
        .consumer(consumer)  
        .commitMode(PERIODIC_CONSUMER_SYNC)  
        .build();
    ParallelStreamProcessor<String, String> eosStreamProcessor = createEosStreamProcessor(options);
    
    
 eosStreamProcessor.poll(context -> recordHandler.processRecord(context.getSingleConsumerRecord()));
```
 
In this example, you're specifying ordering by key with a maximum concurrency of 16.  You specify `PERIODIC_CONSUMER_SYNC` for the committing of offsets. The `PERIODIC_CONSUMER_SYNC` will block the Parallel Consumer’s processing loop until a successful commit response is received. Asynchronous is also supported, which optimizes for consumption throughput (the downside being higher risk of needing to process duplicate messages in error recovery scenarios).

Then you start consuming records in parallel with the `ParallelStreamProcessor.poll` method which takes a `java.util.function.Consumer` instance to work with each
record it consumes.

## Performance tests

There are two performance tests `ParallelConsumerPerfTest` and the `MultithreadedKafkaConsumerPerfTest` you can run to 
observe the power of parallel consumption first-hand.






