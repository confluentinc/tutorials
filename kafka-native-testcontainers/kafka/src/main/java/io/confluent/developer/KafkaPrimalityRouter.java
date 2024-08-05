package io.confluent.developer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.math3.primes.Primes.isPrime;

public class KafkaPrimalityRouter {

    protected static final String INPUT_TOPIC = "input-topic";
    protected static final String PRIME_TOPIC = "primes";
    protected static final String COMPOSITE_TOPIC = "composites";
    protected static final String DLQ_TOPIC = "dlq";
    private volatile boolean keepConsuming = true;

    private Consumer<byte[], byte[]> consumer;
    private Producer<Integer, Integer> producer;
    private Producer<byte[], byte[]> dlqProducer;

    /**
     * Build an app router.
     *
     * @param consumer input topic consumer
     * @param producer int producer to primes / composites topics
     * @param dlqProducer producer to dead-letter queue
     */
    public KafkaPrimalityRouter(
            final Consumer<byte[], byte[]> consumer,
            final Producer<Integer, Integer> producer,
            final Producer<byte[], byte[]> dlqProducer
    ) {
        this.consumer = consumer;
        this.producer = producer;
        this.dlqProducer = dlqProducer;
    }

    /**
     * Helper to build a consumer with auto.offset.reset set to earliest.
     *
     * @param bootstrapServers  bootstrap servers endpoint
     * @param consumerGroupId   consumer group ID
     * @param deserializerClass deseriaizer to use
     * @return Consumer instance
     */
    protected static Consumer buildConsumer(String bootstrapServers, String consumerGroupId, Class deserializerClass) {
        final Properties consumerProps = new Properties() {{
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, deserializerClass);
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializerClass);
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        }};

        return new KafkaConsumer<>(consumerProps);
    }

    /**
     * Helper to build a producer.
     *
     * @param bootstrapServers bootstrap servers endpoint
     * @param serializerClass  serializer to use
     * @return Producer instance
     */
    protected static Producer buildProducer(String bootstrapServers, Class serializerClass) {
        final Properties producerProps = new Properties() {{
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializerClass);
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializerClass);
        }};

        return new KafkaProducer(producerProps);
    }

    /**
     * Main application worker. Continually consumer from input topic and routes to primes / composites
     * topics, or DLQ in the case of a serialization exception.
     *
     * @param topicNames input topic names
     */
    public void runConsume(final List<String> topicNames) {
        IntegerDeserializer intDeserializer = new IntegerDeserializer();
        try {
            consumer.subscribe(topicNames);
            while (keepConsuming) {
                final ConsumerRecords<byte[], byte[]> consumerRecords = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<byte[], byte[]> record : consumerRecords) {
                    byte[] key = record.key();
                    Integer keyInt;
                    try {
                        keyInt = intDeserializer.deserialize("dummy", key);
                    } catch (SerializationException e) {
                        dlqProducer.send(new ProducerRecord<>(DLQ_TOPIC, key, record.value()));
                        continue;
                    }
                    String destinationTopic = isPrime(keyInt) ? PRIME_TOPIC : COMPOSITE_TOPIC;

                    producer.send(new ProducerRecord<>(destinationTopic, keyInt, keyInt));
                }
            }
        } finally {
            consumer.close();
        }
    }

    /**
     * Shutdown method that gets called on interrupt.
     */
    public void shutdown() {
        keepConsuming = false;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "USAGE: This program takes 2 arguments:\n" +
                            "1. bootstrap servers - comma-delimited <host:port>,<host:port>,...\n" +
                            "2. consumer group ID"
            );
        }

        final String bootstrapServers = args[0];
        final String consumerGroupId = args[1];

        final Consumer<byte[], byte[]> consumer = buildConsumer(bootstrapServers, consumerGroupId, ByteArrayDeserializer.class);
        final Producer<Integer, Integer> producer = buildProducer(bootstrapServers, IntegerSerializer.class);
        final Producer<byte[], byte[]> dlqProducer = buildProducer(bootstrapServers, ByteArraySerializer.class);

        final KafkaPrimalityRouter consumerApplication = new KafkaPrimalityRouter(consumer, producer, dlqProducer);

        Runtime.getRuntime().addShutdownHook(new Thread(consumerApplication::shutdown));

        consumerApplication.runConsume(List.of(INPUT_TOPIC));
    }
}
