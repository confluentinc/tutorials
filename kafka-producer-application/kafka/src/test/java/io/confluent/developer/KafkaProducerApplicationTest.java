package io.confluent.developer;


import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

import static org.testcontainers.containers.KafkaContainer.KAFKA_PORT;

public class KafkaProducerApplicationTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    private static String bootstrapServers;

    private static List<String> rawEvents = Arrays.asList("foo-bar", "bar-foo", "baz-bar", "great:weather");
    private static Map<String, String> expected;

    private Properties producerProperties;

    private String outputTopic;

    @BeforeClass
    public static void setup() {

//        Network network = Network.newNetwork();
//
//        KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.1"))
//                .withKraft()
//                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
//                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
//                .withEnv("KAFKA_TRANSACTION_STATE_LOG_NUM_PARTITIONS", "1")
//                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "500")
//                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
//                .withReuse(false)
//                .withNetwork(network);
//        kafka.start();
//
//        bootstrapServers = kafka.getBootstrapServers();

        rawEvents = Arrays.asList("foo-bar", "bar-foo", "baz-bar", "great:weather");
        expected = new HashMap<>() {{
            put("foo", "bar");
            put("bar", "foo");
            put("baz", "bar");
            put(null, "great:weather");
        }};
    }

    @Before
    public void before() {
//        producerProperties = new Properties() {{
//            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
//            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
//            put(ProducerConfig.ACKS_CONFIG, "all");
//            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
////            input.topic.name=input-topic");
////            output.topic.name=output-topic");
//        }};

        outputTopic = "output-topic";
    }

    @Test
    public void testSendEvent() {
        final StringSerializer stringSerializer = new StringSerializer();
        final MockProducer<String, String> mockProducer = new MockProducer<>(true, stringSerializer, stringSerializer);
        final KafkaProducerApplication producerApp = new KafkaProducerApplication(mockProducer);

        rawEvents.stream()
                .map(e -> producerApp.createRecord(outputTopic, e))
                .forEach(producerApp::sendEvent);

        final Map<String, String> actual = mockProducer.history().stream()
                .collect(Collectors.toMap(ProducerRecord::key, ProducerRecord::value));

        assertEquals(actual, expected);
        producerApp.shutdown();
    }


//    @Test
//    @Ignore
//    public void testProduce() throws IOException {
//        final StringSerializer stringSerializer = new StringSerializer();
//        final MockProducer<String, String> mockProducer = new MockProducer<>(true, stringSerializer, stringSerializer);
//        final Properties props = KafkaProducerApplication.loadProperties(TEST_CONFIG_FILE);
//        final String topic = props.getProperty("output.topic.name");
//        final KafkaProducerApplication producerApp = new KafkaProducerApplication(mockProducer);
//        final List<String> records = Arrays.asList("foo-bar", "bar-foo", "baz-bar", "great:weather");
//
//        records.forEach(r -> producerApp.produce(topic, r));
//        final Map<String, String> expected = new HashMap<>() {{
//            put("foo", "bar");
//            put("bar", "foo");
//            put("baz", "bar");
//            put(null, "great:weather");
//        }};
//        final Map<String, String> actual = mockProducer.history().stream()
//                .collect(Collectors.toMap(ProducerRecord::key, ProducerRecord::value));
//
//        assertEquals(actual, expected);
//        producerApp.shutdown();
//    }
}
