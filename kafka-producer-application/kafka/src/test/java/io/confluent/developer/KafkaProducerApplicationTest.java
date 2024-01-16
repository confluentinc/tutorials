package io.confluent.developer;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class KafkaProducerApplicationTest {
    private static final String outputTopic = "output-topic";
    private static List<String> rawEvents;
    private static Map<String, String> expected;

    private MockProducer<String, String> mockProducer;

    @BeforeClass
    public static void setup() {
        rawEvents = Arrays.asList("foo-bar", "bar-foo", "baz-bar", "great:weather");
        expected = new HashMap<>() {{
            put("foo", "bar");
            put("bar", "foo");
            put("baz", "bar");
            put(null, "great:weather");
        }};
    }

    @Before
    public void setupEach() {
        final StringSerializer stringSerializer = new StringSerializer();
        mockProducer = new MockProducer<>(true, stringSerializer, stringSerializer);
    }

    @Test
    public void testSendEvent() {
        final KafkaProducerApplication producerApp = new KafkaProducerApplication(mockProducer);

        rawEvents.stream()
                .map(e -> producerApp.createProducerRecord(outputTopic, e)) // Map each element of rawEvent to ProducerRecord.
                .forEach(producerApp::sendEvent); // Send ProducerRecord.

        // Collect the keys and values the Producer sent to kafka.
        final Map<String, String> actual = mockProducer.history().stream()
                .collect(Collectors.toMap(ProducerRecord::key, ProducerRecord::value));

        assertEquals(actual, expected);
        producerApp.shutdown();
    }

    @Test
    public void testSendEventWithCallback() {
        final KafkaProducerApplication producerApp = new KafkaProducerApplication(mockProducer);

        List<Long> callbackOffsets = new ArrayList<>();

        // Callback function for use in Producer Send.
        // Simple implementation to record the offsets of events sent to kafka.
        Callback callback = (recordMetadata, e) -> callbackOffsets.add(recordMetadata.offset());

        rawEvents.stream()
                .map(event -> producerApp.createProducerRecord(outputTopic, event)) // Map each element of rawEvent to ProducerRecord.
                .forEach(record -> producerApp.sendEvent(record, callback)); // Send ProducerRecord with callback.

        // Collect the keys and values the Producer sent to kafka.
        final Map<String, String> actual = mockProducer.history().stream()
                .collect(Collectors.toMap(ProducerRecord::key, ProducerRecord::value));

        // Assert the rawEvents were transformed and sent to kafka.
        assertEquals(actual, expected);
        // Assert that we recorded as many offsets (via the Callback) as events we sent to kafka.
        assertEquals(callbackOffsets.size(), rawEvents.size());
        producerApp.shutdown();
    }
}
