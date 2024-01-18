package io.confluent.developer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaConsumerApplicationTest {

  @Test
  public void consumerTest() throws Exception {

    // use this list for assertions.
    List<String> actualRecords = new ArrayList<>();
    // Record handler implementation to record the values of the records consumed to the list above.
    final ConsumerRecordsHandler<String, String> recordsHandler = consumerRecords -> consumerRecords.forEach(cr -> actualRecords.add(cr.value()));

    final String topic = "input-topic";
    final TopicPartition topicPartition = new TopicPartition(topic, 0);
    final MockConsumer<String, String> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);

    final KafkaConsumerApplication consumerApplication = new KafkaConsumerApplication(mockConsumer);
    
    mockConsumer.schedulePollTask(() -> addTopicPartitionsAssignmentAndAddConsumerRecords(topic, mockConsumer, topicPartition));
    mockConsumer.schedulePollTask(consumerApplication::shutdown);

    consumerApplication.runConsume(List.of(topic), recordsHandler);

    final List<String> expectedWords = Arrays.asList("foo", "bar", "baz");
    assertEquals(actualRecords, expectedWords);
  }

  private void addTopicPartitionsAssignmentAndAddConsumerRecords(final String topic,
                                 final MockConsumer<String, String> mockConsumer,
                                 final TopicPartition topicPartition) {

    final Map<TopicPartition, Long> beginningOffsets = new HashMap<>();
    beginningOffsets.put(topicPartition, 0L);
    mockConsumer.rebalance(Collections.singletonList(topicPartition));
    mockConsumer.updateBeginningOffsets(beginningOffsets);
    addConsumerRecords(mockConsumer, topic);
  }

  private void addConsumerRecords(final MockConsumer<String, String> mockConsumer, final String topic) {
    mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 0, null, "foo"));
    mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 1, null, "bar"));
    mockConsumer.addRecord(new ConsumerRecord<>(topic, 0, 2, null, "baz"));
  }
}
