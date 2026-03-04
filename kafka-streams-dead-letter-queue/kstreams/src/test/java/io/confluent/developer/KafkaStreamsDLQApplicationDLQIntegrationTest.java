package io.confluent.developer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for Kafka Streams Dead Letter Queue (DLQ) functionality using Testcontainers.
 *
 * These tests validate end-to-end DLQ routing behavior with a real Kafka broker.
 * Unlike TopologyTestDriver unit tests, these tests can verify that records are actually
 * routed to the DLQ topic when exceptions occur (KIP-1034 runtime infrastructure feature).
 *
 * Each test creates its own KafkaContainer for complete isolation.
 */
public class KafkaStreamsDLQApplicationDLQIntegrationTest {

    private static final String DLQ_TOPIC = "dlq-topic";
    private static final int POLL_TIMEOUT_SECONDS = 10;

    @Test
    public void testDLQRoutingWithRealKafka() throws InterruptedException {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.2.0"))) {
            kafka.start();

            // Create topics
            createTopics(kafka.getBootstrapServers(),
                    KafkaStreamsDLQApplication.INPUT_TOPIC,
                    KafkaStreamsDLQApplication.OUTPUT_TOPIC,
                    DLQ_TOPIC);

            // Configure and start Kafka Streams application
            Properties streamProps = buildStreamProperties(kafka.getBootstrapServers());
            streamProps.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLQ_TOPIC);

            KafkaStreamsDLQApplication app = new KafkaStreamsDLQApplication();
            Topology topology = app.buildTopology(streamProps);
            KafkaStreams streams = new KafkaStreams(topology, streamProps);
            streams.start();

            // Wait for streams app to be ready
            Thread.sleep(2000);

            // Produce a record that will cause an error (missing ball field)
            produceRecordToInput(kafka.getBootstrapServers(),
                    "error-key",
                    "{\"sport\": \"swimming\"}");

            // Verify record appears in DLQ topic
            List<ConsumerRecord<String, String>> dlqRecords = consumeFromTopic(
                    kafka.getBootstrapServers(),
                    DLQ_TOPIC,
                    1);

            assertEquals(1, dlqRecords.size(), "Expected exactly one record in DLQ");
            assertThat(dlqRecords.get(0).value(), containsString("swimming"));
            assertEquals("error-key", dlqRecords.get(0).key());

            // Verify record did NOT appear in output topic
            List<ConsumerRecord<String, String>> outputRecords = consumeFromTopic(
                    kafka.getBootstrapServers(),
                    KafkaStreamsDLQApplication.OUTPUT_TOPIC,
                    0);

            assertEquals(0, outputRecords.size(), "Expected no records in output topic");

            streams.close();
        }
    }

    @Test
    public void testNormalProcessingWithRealKafka() throws InterruptedException {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.2.0"))) {
            kafka.start();

            // Create topics
            createTopics(kafka.getBootstrapServers(),
                    KafkaStreamsDLQApplication.INPUT_TOPIC,
                    KafkaStreamsDLQApplication.OUTPUT_TOPIC,
                    DLQ_TOPIC);

            // Configure and start Kafka Streams application
            Properties streamProps = buildStreamProperties(kafka.getBootstrapServers());
            streamProps.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLQ_TOPIC);

            KafkaStreamsDLQApplication app = new KafkaStreamsDLQApplication();
            Topology topology = app.buildTopology(streamProps);
            KafkaStreams streams = new KafkaStreams(topology, streamProps);
            streams.start();

            // Wait for streams app to be ready
            Thread.sleep(2000);

            // Produce a normal record with valid ball field
            produceRecordToInput(kafka.getBootstrapServers(),
                    "normal-key",
                    "{\"sport\": \"baseball\", \"ball\": {\"shape\": \"round\", \"dimensions\": {\"diameter\": \"2.9in\", \"weight\": \"5oz\"}}}");

            // Verify record appears in output topic
            List<ConsumerRecord<String, String>> outputRecords = consumeFromTopic(
                    kafka.getBootstrapServers(),
                    KafkaStreamsDLQApplication.OUTPUT_TOPIC,
                    1);

            assertEquals(1, outputRecords.size(), "Expected exactly one record in output topic");
            assertEquals("normal-key", outputRecords.get(0).key());
            assertThat(outputRecords.get(0).value(), containsString("baseball"));
            assertThat(outputRecords.get(0).value(), containsString("ball"));

            // Verify record did NOT appear in DLQ topic
            List<ConsumerRecord<String, String>> dlqRecords = consumeFromTopic(
                    kafka.getBootstrapServers(),
                    DLQ_TOPIC,
                    0);

            assertEquals(0, dlqRecords.size(), "Expected no records in DLQ topic");

            streams.close();
        }
    }

    @Test
    public void testInvalidJsonRoutesToDLQ() throws InterruptedException {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.2.0"))) {
            kafka.start();

            // Create topics
            createTopics(kafka.getBootstrapServers(),
                    KafkaStreamsDLQApplication.INPUT_TOPIC,
                    KafkaStreamsDLQApplication.OUTPUT_TOPIC,
                    DLQ_TOPIC);

            // Configure and start Kafka Streams application
            Properties streamProps = buildStreamProperties(kafka.getBootstrapServers());
            streamProps.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLQ_TOPIC);

            KafkaStreamsDLQApplication app = new KafkaStreamsDLQApplication();
            Topology topology = app.buildTopology(streamProps);
            KafkaStreams streams = new KafkaStreams(topology, streamProps);
            streams.start();

            // Wait for streams app to be ready
            Thread.sleep(2000);

            // Produce invalid JSON
            produceRecordToInput(kafka.getBootstrapServers(),
                    "invalid-key",
                    "not valid json at all");

            // Verify record appears in DLQ topic
            List<ConsumerRecord<String, String>> dlqRecords = consumeFromTopic(
                    kafka.getBootstrapServers(),
                    DLQ_TOPIC,
                    1);

            assertEquals(1, dlqRecords.size(), "Expected exactly one record in DLQ");
            assertEquals("not valid json at all", dlqRecords.get(0).value());

            // Verify record did NOT appear in output topic
            List<ConsumerRecord<String, String>> outputRecords = consumeFromTopic(
                    kafka.getBootstrapServers(),
                    KafkaStreamsDLQApplication.OUTPUT_TOPIC,
                    0);

            assertEquals(0, outputRecords.size(), "Expected no records in output topic");

            streams.close();
        }
    }

    /**
     * Create Kafka topics programmatically
     */
    private void createTopics(String bootstrapServers, String... topicNames) {
        Properties adminProps = new Properties();
        adminProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(adminProps)) {
            List<NewTopic> topics = List.of(topicNames).stream()
                    .map(name -> new NewTopic(name, 1, (short) 1))
                    .toList();

            admin.createTopics(topics).all().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to create topics", e);
        }
    }

    /**
     * Build properties for Kafka Streams application
     */
    private Properties buildStreamProperties(String bootstrapServers) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-dlq-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, org.apache.kafka.common.serialization.Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, org.apache.kafka.common.serialization.Serdes.String().getClass());
        return props;
    }

    /**
     * Produce a record to the input topic
     */
    private void produceRecordToInput(String bootstrapServers, String key, String value) {
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaStreamsDLQApplication.INPUT_TOPIC,
                    key,
                    value);
            producer.send(record).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to produce record", e);
        }
    }

    /**
     * Consume records from a topic with timeout
     */
    private List<ConsumerRecord<String, String>> consumeFromTopic(
            String bootstrapServers,
            String topicName,
            int expectedCount) {

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(topicName));

            long startTime = System.currentTimeMillis();
            long timeout = POLL_TIMEOUT_SECONDS * 1000;
            List<ConsumerRecord<String, String>> records = new java.util.ArrayList<>();

            while (System.currentTimeMillis() - startTime < timeout) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                polled.forEach(records::add);

                if (records.size() >= expectedCount) {
                    break;
                }
            }

            return records;
        }
    }
}
