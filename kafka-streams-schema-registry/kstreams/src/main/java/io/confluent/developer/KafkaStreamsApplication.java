package io.confluent.developer;

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

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.developer.avro.MyRecord;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.BASIC_AUTH_CREDENTIALS_SOURCE;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.USER_INFO_CONFIG;

public class KafkaStreamsApplication {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsApplication.class);

    private static final Serde<String> stringSerde = Serdes.String();
    public static final String INPUT_TOPIC = "input";
    public static final String OUTPUT_TOPIC = "output";

    private static boolean isCloud = false;


    public static SpecificAvroSerde<MyRecord> myRecordSerde(final Properties envProps) {
        final SpecificAvroSerde<MyRecord> serde = new SpecificAvroSerde<>();
        if (isCloud) {
            serde.configure(Map.of(
                    SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty(SCHEMA_REGISTRY_URL_CONFIG),
                    BASIC_AUTH_CREDENTIALS_SOURCE, envProps.getProperty(BASIC_AUTH_CREDENTIALS_SOURCE),
                    USER_INFO_CONFIG, envProps.getProperty(USER_INFO_CONFIG)
            ), false);
        } else {
            serde.configure(Collections.singletonMap(
                    SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty(SCHEMA_REGISTRY_URL_CONFIG)
            ), false);
        }
        return serde;
    }

    public Topology buildTopology(Properties allProps, SpecificAvroSerde<MyRecord> valueSerde) {
        StreamsBuilder builder = new StreamsBuilder();

        builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, valueSerde))
                .peek((k, v) -> LOG.info("Observed event: {}", v))
                .mapValues(record -> new MyRecord(record.getMyString().toUpperCase()))
                .peek((k, v) -> LOG.info("Transformed event: {}", v))
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, valueSerde));

        return builder.build(allProps);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
            if (args[0].contains("cloud.properties")) {
                isCloud = true;
            }
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-sr-application");
        KafkaStreamsApplication kafkaStreamsApplication = new KafkaStreamsApplication();
        SpecificAvroSerde<MyRecord> valueSerde = myRecordSerde(properties);

        Topology topology = kafkaStreamsApplication.buildTopology(properties, valueSerde);

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only don't do this in production as it wipes out all local state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
