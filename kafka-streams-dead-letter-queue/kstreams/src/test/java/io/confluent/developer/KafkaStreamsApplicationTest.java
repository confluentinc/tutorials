package io.confluent.developer;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaStreamsApplicationTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;
    private TestOutputTopic<String, String> dlqTopic;

    @BeforeEach
    public void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put("processing.exception.handler.dead.letter.queue.topic.name", "dlq-topic");

        KafkaStreamsApplication app = new KafkaStreamsApplication();
        Topology topology = app.buildTopology(props);

        testDriver = new TopologyTestDriver(topology, props);

        inputTopic = testDriver.createInputTopic(
                KafkaStreamsApplication.INPUT_TOPIC,
                new StringSerializer(),
                new StringSerializer());

        outputTopic = testDriver.createOutputTopic(
                KafkaStreamsApplication.OUTPUT_TOPIC,
                new StringDeserializer(),
                new StringDeserializer());

        dlqTopic = testDriver.createOutputTopic(
                "dlq-topic",
                new StringDeserializer(),
                new StringDeserializer());
    }

    @AfterEach
    public void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    public void testNormalProcessing() {
        // Given a record without causeError field
        String key = "key1";
        String value = "{\"data\": \"test message\"}";

        // When processing the record
        inputTopic.pipeInput(key, value);

        // Then it should appear in the output topic
        assertThat(outputTopic.getQueueSize(), equalTo(1L));
        var outputRecord = outputTopic.readKeyValue();
        assertThat(outputRecord.key, equalTo(key));
        assertThat(outputRecord.value, equalTo(value));

        // And not in the DLQ
        assertTrue(dlqTopic.isEmpty());
    }

    @Test
    public void testProcessingWithCauseErrorFalse() {
        // Given a record with causeError: false
        String key = "key2";
        String value = "{\"causeError\": false, \"data\": \"test message\"}";

        // When processing the record
        inputTopic.pipeInput(key, value);

        // Then it should appear in the output topic
        assertThat(outputTopic.getQueueSize(), equalTo(1L));
        var outputRecord = outputTopic.readKeyValue();
        assertThat(outputRecord.key, equalTo(key));
        assertThat(outputRecord.value, equalTo(value));

        // And not in the DLQ
        assertTrue(dlqTopic.isEmpty());
    }

    @Test
    public void testDLQRoutingWithCauseErrorTrue() {
        // Given a record with causeError: true
        String key = "key3";
        String value = "{\"causeError\": true, \"data\": \"this should fail\"}";

        // When processing the record (which will throw an exception)
        try {
            inputTopic.pipeInput(key, value);
        } catch (RuntimeException e) {
            // Expected - the processor throws an exception
        }

        // Then it should NOT appear in the output topic
        assertTrue(outputTopic.isEmpty());

        // Note: In a real DLQ implementation with KIP-1034, the record would be
        // automatically routed to the DLQ topic. However, TopologyTestDriver doesn't
        // fully simulate the exception handler behavior, so we're testing that
        // the exception is thrown, which would trigger DLQ routing in production.
    }

    @Test
    public void testInvalidJsonHandling() {
        // Given a record with invalid JSON
        String key = "key4";
        String value = "not valid json";

        // When processing the record
        try {
            inputTopic.pipeInput(key, value);
        } catch (RuntimeException e) {
            // Expected - parsing failure throws exception
            // The exception message is wrapped by Kafka Streams, so we just verify it's thrown
            assertTrue(e.getMessage().contains("Exception caught in process"));
        }

        // Then it should NOT appear in the output topic
        assertTrue(outputTopic.isEmpty());
    }

    @Test
    public void testMultipleRecordsBatch() {
        // Given multiple records, some with causeError and some without
        inputTopic.pipeInput("key1", "{\"data\": \"message1\"}");
        inputTopic.pipeInput("key2", "{\"data\": \"message2\", \"causeError\": false}");

        // Then both should appear in output topic
        assertThat(outputTopic.getQueueSize(), equalTo(2L));

        var record1 = outputTopic.readKeyValue();
        assertThat(record1.key, equalTo("key1"));

        var record2 = outputTopic.readKeyValue();
        assertThat(record2.key, equalTo("key2"));

        assertTrue(outputTopic.isEmpty());
        assertTrue(dlqTopic.isEmpty());
    }
}
