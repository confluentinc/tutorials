package io.confluent.developer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsApplication {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsApplication.class);
    private static final String DLQ_TOPIC = "dlq-topic";

    private final Serde<String> stringSerde = Serdes.String();
    public static final String INPUT_TOPIC = "input";
    public static final String OUTPUT_TOPIC = "output";

    public Topology buildTopology(Properties allProps) {
        StreamsBuilder builder = new StreamsBuilder();
        ObjectMapper objectMapper = new ObjectMapper();

        builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
                .mapValues(value -> {
                    try {
                        Map<String, Object> valueMap = objectMapper.readValue(
                                value,
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                        Object causeError = valueMap.get("causeError");
                        if (causeError != null && Boolean.TRUE.equals(causeError)) {
                            LOG.error("causeError field detected with value true - throwing exception for DLQ demo");
                            throw new RuntimeException("Simulated processing error for DLQ routing");
                        }
                        LOG.info("Successfully processed event: {}", value);
                        return value;
                    } catch (IOException e) {
                        LOG.error("Failed to parse JSON value: {}", value, e);
                        throw new RuntimeException("Failed to parse JSON", e);
                    }
                })
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));

        return builder.build(allProps);
    }

    public static void main(String[] args) {

        try (InputStream input = KafkaStreamsApplication.class.getClassLoader().getResourceAsStream("confluent.properties")) {
            
            Properties properties = new Properties();
            try {
                properties.load(input);
            } catch (Exception e) {
                LOG.error("Failed to load properties", e);
                return;
            }

            // Application configuration
            properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-dlq-demo");

            // KIP-1034: Configure Dead Letter Queue
            properties.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLQ_TOPIC);

            LOG.info("Starting Kafka Streams application with DLQ enabled");
            LOG.info("DLQ Topic: {}", DLQ_TOPIC);
            LOG.info("To trigger DLQ routing, send a JSON record with 'causeError: true'");

            KafkaStreamsApplication kafkaStreamsApplication = new KafkaStreamsApplication();
            Topology topology = kafkaStreamsApplication.buildTopology(properties);

            try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    kafkaStreams.close(Duration.ofSeconds(5));
                    countDownLatch.countDown();
                }));
                // For local running only - don't do this in production as it wipes out all local state
                kafkaStreams.cleanUp();
                kafkaStreams.start();
                countDownLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
