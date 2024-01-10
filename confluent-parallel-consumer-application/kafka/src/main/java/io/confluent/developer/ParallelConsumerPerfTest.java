package io.confluent.developer;


import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelEoSStreamProcessor;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import me.tongfei.progressbar.ProgressBar;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Properties;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.awaitility.Awaitility.await;


/**
 * Consumption throughput test that runs the Confluent Parallel Consumer, synchronously sleeping
 * `record.handler.sleep.ms` per event. This simulates the performance characteristics of applications that do
 * something of nontrivial latency per event, e.g., call out to a DB or REST API per event. It is the Confluent
 * Parallel Consumer analogue to MultithreadedKafkaConsumerPerfTest, which is based on KafkaConsumer.
 */
public class ParallelConsumerPerfTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParallelConsumerPerfTest.class.getName());
  private final ParallelStreamProcessor<String, String> parallelConsumer;
  private final SleepingRecordHandler recordHandler;

  /**
   * Throughput performance test for the Confluent Parallel Consumer.
   *
   * @param parallelConsumer the parallel consumer
   * @param recordHandler    a handler to call per record
   */
  public ParallelConsumerPerfTest(final ParallelStreamProcessor<String, String> parallelConsumer,
                                  final SleepingRecordHandler recordHandler) {
    this.parallelConsumer = parallelConsumer;
    this.recordHandler = recordHandler;
  }

  /**
   * Close the parallel consumer on application shutdown
   */
  public void shutdown() {
    LOGGER.info("shutting down");
    if (parallelConsumer != null) {
      parallelConsumer.close();
    }
  }

  /**
   * Subscribes to the configured input topic and calls (blocking) `poll` method.
   *
   * @param appProperties application and consumer properties
   */
  private void runConsume(final Properties appProperties) {
    parallelConsumer.subscribe(Collections.singletonList(appProperties.getProperty("input.topic")));
    parallelConsumer.poll(context -> {
      recordHandler.processRecord(context.getSingleConsumerRecord());
    });
  }

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

    // random consumer group ID for rerun convenience
    String groupId = "parallel-consumer-perf-test-group-" + nextInt();
    appProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

    // create parallel consumer configured based on properties from app configuration
    final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(appProperties);
    final int maxConcurrency = Integer.parseInt(appProperties.getProperty("parallel.consumer.max.concurrency"));
    final ParallelConsumerOptions.ProcessingOrder processingOrder = ParallelConsumerOptions.ProcessingOrder.
            valueOf(appProperties.getProperty("parallel.consumer.order"));
    final ParallelConsumerOptions.CommitMode commitMode = ParallelConsumerOptions.CommitMode.
            valueOf(appProperties.getProperty("parallel.consumer.commit.mode"));
    final int secondsBetweenCommits = Integer.parseInt(appProperties.getProperty("parallel.consumer.seconds.between.commits"));
    final ParallelEoSStreamProcessor parallelConsumer = new ParallelEoSStreamProcessor<>(ParallelConsumerOptions.<String, String>builder()
            .ordering(processingOrder)
            .maxConcurrency(maxConcurrency)
            .consumer(consumer)
            .commitMode(commitMode)
            .commitInterval(ofSeconds(secondsBetweenCommits))
            .build());

    // create handler that sleeps configured number of ms per record
    final int recordHandlerSleepMs = Integer.parseInt(appProperties.getProperty("record.handler.sleep.ms"));
    final SleepingRecordHandler recordHandler = new SleepingRecordHandler(recordHandlerSleepMs);

    // kick off the consumer, marking the start time so that we can report total runtime to consume all records
    final ParallelConsumerPerfTest consumerApplication = new ParallelConsumerPerfTest(parallelConsumer, recordHandler);
    Runtime.getRuntime().addShutdownHook(new Thread(consumerApplication::shutdown));
    long startTimeMs = System.currentTimeMillis();
    consumerApplication.runConsume(appProperties);

    // wait for the consumer to process all records, updating the progress bar as we go
    int recordsToConsume = Integer.parseInt(appProperties.getProperty("records.to.consume"));
    ProgressBar progressBar = new ProgressBar("Progress", recordsToConsume);
    await()
            .forever()
            .pollInterval(250, MILLISECONDS)
            .until(() -> {
              int numRecordsProcessed = recordHandler.getNumRecordsProcessed();
              progressBar.stepTo(numRecordsProcessed);
              return numRecordsProcessed >= recordsToConsume;
            });

    // done! report total time to consume all records
    progressBar.close();
    double durationSeconds = (System.currentTimeMillis() - startTimeMs) / 1_000.0;
    DecimalFormat df = new DecimalFormat("0.00");
    LOGGER.info("Time to consume {} records: {} seconds", recordsToConsume, df.format(durationSeconds));

    consumerApplication.shutdown();
  }

}
