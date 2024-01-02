package io.confluent.developer;


import me.tongfei.progressbar.ProgressBar;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This class consumes a specified number of records from a Kafka topic with a consumer thread per explicitly
 * assigned partition.
 */
public class MultithreadedKafkaConsumer implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedKafkaConsumer.class.getName());

  private final Properties appProperties;
  private final SleepingRecordHandler recordHandler;
  private final AtomicInteger recordsConsumed;  // total records consumed across all threads
  private ExecutorService executor;

  /**
   * Multithreaded Kafka consumer that runs a KafkaConsumer per partition.
   *
   * @param appProperties application and consumer properties
   * @param recordHandler records handler to run per record
   */
  public MultithreadedKafkaConsumer(Properties appProperties, SleepingRecordHandler recordHandler) {
    this.appProperties = appProperties;
    this.recordHandler = recordHandler;
    this.recordsConsumed = new AtomicInteger(0);
  }

  /**
   * Main consumption method that instantiates consumers per partition, runs the consumers in a thread pool, and outputs
   * progress to the user as it consumes.
   */
  public void runConsume() {
    int recordsToConsume = Integer.parseInt(appProperties.getProperty("records.to.consume"));

    // instantiate all consumers before marking start time to keep init time out of the reported duration
    List<KafkaConsumer> consumers = getConsumersPerPartition(appProperties);

    // kick off consumers to process all records, updating a progress bar as we go
    ProgressBar progressBar = new ProgressBar("Progress", recordsToConsume);

    // create thread pool and shutdown hook to clean up in case of exception
    executor = Executors.newFixedThreadPool(consumers.size());
    Thread shutdownExecutor = new Thread(() -> this.close());
    Runtime.getRuntime().addShutdownHook(shutdownExecutor);

    // mark the consumption start time and kick off all consumer threads
    long startTimeMs = System.currentTimeMillis();
    List<Future> futures = new ArrayList<>();
    for (KafkaConsumer consumer: consumers) {
      futures.add(executor.submit(new ConsumerTask(consumer, recordsToConsume, recordsConsumed, progressBar)));
    }

    // wait for all tasks to complete, which happens when all records have been consumed
    LOGGER.info("Waiting for consumers to consume all {} records", recordsToConsume);
    for (Future future : futures) {
      try {
        future.get();
      } catch (InterruptedException|ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    // done! report total time to consume all records
    double durationSeconds = (System.currentTimeMillis() - startTimeMs) / 1_000.0;
    DecimalFormat df = new DecimalFormat("0.00");
    LOGGER.info("Total time to consume {} records: {} seconds", recordsToConsume, df.format(durationSeconds));
  }

  /**
   * Create KafkaConsumer instances per partition, and explicitly assign a partition to each.
   *
   * @param appProperties consumer and application properties
   * @return list of consumers
   */
  private List<KafkaConsumer> getConsumersPerPartition(Properties appProperties) {
    String topic = appProperties.getProperty("input.topic");

    // use temp consumer to inspect the number of partitions
    int numPartitions;
    try (KafkaConsumer tempConsumer = new KafkaConsumer<>(appProperties)) {
      numPartitions = tempConsumer.partitionsFor(topic).size();
      LOGGER.info("{} partitions detected for {} topic", numPartitions, topic);
    }

    List<KafkaConsumer> consumers = new ArrayList<>();
    for (int i = 0; i < numPartitions; i++) {
      KafkaConsumer consumer = new KafkaConsumer<String, String>(appProperties);
      TopicPartition tp = new TopicPartition(topic, i);
      consumer.assign(Collections.singletonList(tp));
      consumers.add(consumer);
    }

    return consumers;
  }

  /**
   * Close the multithreaded consumer, which entails shutting down the executor thread pool.
   */
  public void close() {
    if (executor != null) {
      executor.shutdown();
    }
  }

  /**
   * Inner class that runs each consumer.
   */
  class ConsumerTask implements Runnable {
    private final KafkaConsumer consumer;
    private final int recordsToConsume;           // records expected to be consumed across all threads
    private final AtomicInteger recordsConsumed;  // records consumed across all threads
    private final ProgressBar progressBar;

    /**
     * Runnable task to consume a partition.
     *
     * @param consumer          the consumer already assigned a specific partition
     * @param recordsToConsume  total number of records to consume across all threads
     * @param recordsConsumed   running tally of records consumed across all threads
     * @param progressBar       progress bar that we update to recordsConsumed / recordsToConsume during consumption
     */
    public ConsumerTask(KafkaConsumer consumer, int recordsToConsume, AtomicInteger recordsConsumed, ProgressBar progressBar) {
      this.consumer = consumer;
      this.recordsToConsume = recordsToConsume;
      this.recordsConsumed = recordsConsumed;
      this.progressBar = progressBar;
    }

    /**
     * Each task polls until the total number of records consumed across all threads is what we expect. Simply calls
     * the record handler for each record.
     */
    @Override
    public void run() {
      int numConsumed;
      do {
        // Use a poll timeout high enough to not saturate CPU, but fine enough to get interesting comparison numbers.
        // Since the perf tests typically take many seconds to run, use 0.5 second poll timeout to strike this balance.
        ConsumerRecords<String, String> records = consumer.poll(Duration.of(500, ChronoUnit.MILLIS));
        for (ConsumerRecord record : records) {
          recordHandler.processRecord(record);
          numConsumed = recordsConsumed.incrementAndGet();
          progressBar.stepTo(numConsumed);
        }
      } while (recordsConsumed.get() < recordsToConsume); // tasks block until all records consumed
    }
  }

}
