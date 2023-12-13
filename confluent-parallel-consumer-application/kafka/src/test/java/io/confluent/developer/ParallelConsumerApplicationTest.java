package io.confluent.developer;

import io.confluent.csid.utils.LongPollingMockConsumer;
import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder.KEY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests for the ParallelConsumerApplication.
 */
public class ParallelConsumerApplicationTest {

  private static final String TEST_CONFIG_FILE = "src/test/resources/test.properties";

  /**
   * Test the app end to end with a few records consumable via a mock consumer. The app
   * just writes records to a file, so we validate that the expected (mocked) events wind
   * up written to a file.
   *
   * @throws Exception if unable to read properties or create the temp output file
   */
  @Test
  public void consumerTest() throws Exception {
    final Path tempFilePath = Files.createTempFile("test-consumer-output", ".out");
    final ConsumerRecordHandler<String, String> recordHandler = new FileWritingRecordHandler(tempFilePath);
    final Properties appProperties = PropertiesUtil.loadProperties(TEST_CONFIG_FILE);
    final String topic = appProperties.getProperty("input.topic.name");

    final LongPollingMockConsumer<String, String> mockConsumer = new LongPollingMockConsumer<>(OffsetResetStrategy.EARLIEST);
    final ParallelStreamProcessor<String, String> eosStreamProcessor = setupParallelConsumer(mockConsumer, topic);
    final ParallelConsumerApplication consumerApplication = new ParallelConsumerApplication(eosStreamProcessor, recordHandler);
    Runtime.getRuntime().addShutdownHook(new Thread(consumerApplication::shutdown));

    mockConsumer.subscribeWithRebalanceAndAssignment(Collections.singletonList(topic), 1);
    mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 0, null, "foo"));
    mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 1, null, "bar"));
    mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 2, null, "baz"));

    consumerApplication.runConsume(appProperties);

    final Set<String> expectedRecords = new HashSet<>();
    expectedRecords.add("foo");
    expectedRecords.add("bar");
    expectedRecords.add("baz");
    final Set<String> actualRecords = new HashSet<>();
    Awaitility.await()
        .atMost(5, SECONDS)
        .pollInterval(Duration.ofSeconds(1))
        .until(() -> {
          actualRecords.clear();
          actualRecords.addAll(Files.readAllLines(tempFilePath));
          return actualRecords.size() == 3;
        });
    assertEquals(actualRecords, expectedRecords);
  }

  private ParallelStreamProcessor<String, String> setupParallelConsumer(MockConsumer<String, String> mockConsumer, final String topic) {
    ParallelConsumerOptions options = ParallelConsumerOptions.<String, String>builder()
        .ordering(KEY) // <2>
        .maxConcurrency(1000) // <3>
        .consumer(mockConsumer)
        .build();

    ParallelStreamProcessor<String, String> parallelConsumer = ParallelStreamProcessor.createEosStreamProcessor(options);
    parallelConsumer.subscribe(Collections.singletonList(topic));

    return parallelConsumer;
  }

}
