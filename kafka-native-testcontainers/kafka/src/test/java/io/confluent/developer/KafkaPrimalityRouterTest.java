package io.confluent.developer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static io.confluent.developer.KafkaPrimalityRouter.COMPOSITE_TOPIC;
import static io.confluent.developer.KafkaPrimalityRouter.DLQ_TOPIC;
import static io.confluent.developer.KafkaPrimalityRouter.INPUT_TOPIC;
import static io.confluent.developer.KafkaPrimalityRouter.PRIME_TOPIC;
import static io.confluent.developer.KafkaPrimalityRouter.buildConsumer;
import static io.confluent.developer.KafkaPrimalityRouter.buildProducer;
import static org.apache.commons.math3.primes.Primes.isPrime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class KafkaPrimalityRouterTest {

    @Test
    public void testPrimalityRouter() {

        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"))) {
            kafka.start();

            // kick off application main method in a thread
            Thread appThread = new Thread(() -> {
                try {
                    KafkaPrimalityRouter.main(new String[]{kafka.getBootstrapServers(), "my-group-id"});
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            appThread.start();

            // produce 100 ints that should go to prime / composite topics
            try (final Producer<Integer, Integer> producer = buildProducer(kafka.getBootstrapServers(), IntegerSerializer.class)) {
                for (int i = 1; i <= 100; i++) {
                    ProducerRecord record = new ProducerRecord<>(INPUT_TOPIC, i, i);
                    producer.send(record, (event, ex) -> fail(ex));
                }
            }

            // produce strings to test DLQ routing
            try (final Producer<String, String> producer = buildProducer(kafka.getBootstrapServers(), StringSerializer.class)) {
                ProducerRecord record = new ProducerRecord<>(INPUT_TOPIC, "hello", "world");
                producer.send(record, (event, ex) -> fail(ex));
            }

            // validate prime / composite routing
            try (final Consumer<Integer, Integer> consumer = buildConsumer(kafka.getBootstrapServers(),
                    "test-group-id", IntegerDeserializer.class)) {
                consumer.subscribe(List.of(PRIME_TOPIC, COMPOSITE_TOPIC));

                int numConsumed = 0;
                for (int i = 0; i < 10 && numConsumed < 100; i++) {
                    final ConsumerRecords<Integer, Integer> consumerRecords = consumer.poll(Duration.ofSeconds(5));
                    numConsumed += consumerRecords.count();

                    for (ConsumerRecord<Integer, Integer> record : consumerRecords) {
                        Integer key = record.key();
                        String expectedTopic = isPrime(key) ? PRIME_TOPIC : COMPOSITE_TOPIC;
                        assertEquals(expectedTopic, record.topic());
                    }
                }
                assertEquals(100, numConsumed);

                // make sure no more events show up in prime / composite topics
                assertEquals(0, consumer.poll(Duration.ofMillis(200)).count());
            }

            // valdate DLQ routing
            try (final Consumer<String, String> dlqConsumer = buildConsumer(kafka.getBootstrapServers(),
                    "test-group-id", StringDeserializer.class)) {
                dlqConsumer.subscribe(List.of(DLQ_TOPIC));

                int numConsumed = 0;
                for (int i = 0; i < 10 && numConsumed < 1; i++) {
                    final ConsumerRecords<String, String> consumerRecords = dlqConsumer.poll(Duration.ofSeconds(5));
                    numConsumed += consumerRecords.count();

                    for (ConsumerRecord<String, String> record : consumerRecords) {
                        assertEquals("hello", record.key());
                        assertEquals("world", record.value());
                    }
                }
                assertEquals(1, numConsumed);

                // make sure no more events show up in DLQ topic
                assertEquals(0, dlqConsumer.poll(Duration.ofMillis(200)).count());
            }

            kafka.stop();
        }
    }
}
