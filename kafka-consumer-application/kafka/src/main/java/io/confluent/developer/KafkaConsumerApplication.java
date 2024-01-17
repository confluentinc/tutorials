package io.confluent.developer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class KafkaConsumerApplication {

  private volatile boolean keepConsuming = true;

  private Consumer<String, String> consumer;

  public KafkaConsumerApplication(final Consumer<String, String> consumer) {
    this.consumer = consumer;
  }

  public void runConsume(final List<String> topicNames, final ConsumerRecordsHandler<String, String> recordsHandler) {
    try {
      consumer.subscribe(topicNames);
      while (keepConsuming) {
        final ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofSeconds(1));
        recordsHandler.process(consumerRecords);
      }
    } finally {
      consumer.close();
    }
  }

  public void shutdown() {
    keepConsuming = false;
  }

  public static void main(String[] args) throws Exception {

    if (args.length < 2) {
      throw new IllegalArgumentException(
          "USAGE: This program takes 3 arguments:\n" +
                  "1. bootstrap servers - comma-delimited <host:port>,<host:port>,...\n" +
                  "2. consumer group id.\n" +
                  "3. path for output file, used in ConsumerRecordsHandler implementation.");
    }

    final String bootstrapServers = args[0];
    final String consumerGroupId = args[1];
    final Properties consumerAppProps = new Properties() {{
      put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
      put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "500");
      put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2");
    }};

    final String filePath = args[2];
    final Consumer<String, String> consumer = new KafkaConsumer<>(consumerAppProps);
    final ConsumerRecordsHandler<String, String> recordsHandler = new FileWritingRecordsHandler(Paths.get(filePath));
    final KafkaConsumerApplication consumerApplication = new KafkaConsumerApplication(consumer);

    Runtime.getRuntime().addShutdownHook(new Thread(consumerApplication::shutdown));

    consumerApplication.runConsume(List.of("input-topic"), recordsHandler);
  }
}
