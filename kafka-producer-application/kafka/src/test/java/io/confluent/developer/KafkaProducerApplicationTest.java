package io.confluent.developer;


import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;


public class KafkaProducerApplicationTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @Test
    public void testProduce() throws IOException {
        final StringSerializer stringSerializer = new StringSerializer();
        final MockProducer<String, String> mockProducer = new MockProducer<>(true, stringSerializer, stringSerializer);
        final Properties props = KafkaProducerApplication.loadProperties(TEST_CONFIG_FILE);
        final String topic = props.getProperty("output.topic.name");
        final KafkaProducerApplication producerApp = new KafkaProducerApplication(mockProducer, topic);
        final List<String> records = Arrays.asList("foo-bar", "bar-foo", "baz-bar", "great:weather");

        records.forEach(producerApp::produce);
        final Map<String, String> expected = new HashMap<>() {{
            put("foo", "bar");
            put("bar", "foo");
            put("baz", "bar");
            put(null, "great:weather");
        }};
        final Map<String, String> actual = mockProducer.history().stream()
                .collect(Collectors.toMap(pr -> pr.key(), pr -> pr.value()));

        assertEquals(actual, expected);
        producerApp.shutdown();
    }
}
