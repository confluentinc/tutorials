package io.confluent.developer;


import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.io.IOException;
import java.util.Properties;

import static org.apache.commons.lang3.RandomUtils.nextInt;


/**
 * Consumption throughput test that runs a KafkaConsumer per thread, synchronously sleeping `record.handler.sleep.ms`
 * per event. This simulates the performance characteristics of applications that do something of nontrivial latency
 * per event, e.g., call out to a DB or REST API per event. It is the KafkaConsumer analogue to
 * ParallelConsumerPerfTest, which is based on the Confluent Parallel Consumer.
 */
public class MultithreadedKafkaConsumerPerfTest {

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException(
              "This program takes one argument: the path to an environment configuration file.");
    }

    // load app and consumer specific properties from command line arg
    final Properties appProperties;
    try {
      appProperties = PropertiesUtil.loadProperties(args[0]);
    } catch (IOException e) {
      throw new RuntimeException("Unable to load application properties", e);
    }

    // random group ID for rerun convenience
    String groupId = "mt-kafka-consumer-perf-test-group-" + nextInt();
    appProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

    // create handler that sleeps configured number of ms per record
    final int recordHandlerSleepMs = Integer.parseInt(appProperties.getProperty("record.handler.sleep.ms"));
    final SleepingRecordHandler recordHandler = new SleepingRecordHandler(recordHandlerSleepMs);

    // create and run MultithreadedKafkaConsumer, which runs an ExecutorService with a consumer thread per partition
    // and terminates when the total expected number of records have been consumed across the threads
    try (MultithreadedKafkaConsumer mtConsumer = new MultithreadedKafkaConsumer(appProperties, recordHandler)) {
      mtConsumer.runConsume();
    }
  }


}
