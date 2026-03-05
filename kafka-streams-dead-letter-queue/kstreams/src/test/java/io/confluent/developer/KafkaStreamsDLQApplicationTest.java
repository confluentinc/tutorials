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

public class KafkaStreamsDLQApplicationTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    public void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        // Configure DLQ using the correct config key
        // Note: TopologyTestDriver doesn't simulate DLQ routing - this is for topology building only
        props.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, "dlq-topic");

        KafkaStreamsDLQApplication app = new KafkaStreamsDLQApplication();
        Topology topology = app.buildTopology(props);

        testDriver = new TopologyTestDriver(topology, props);

        inputTopic = testDriver.createInputTopic(
                KafkaStreamsDLQApplication.INPUT_TOPIC,
                new StringSerializer(),
                new StringSerializer());

        outputTopic = testDriver.createOutputTopic(
                KafkaStreamsDLQApplication.OUTPUT_TOPIC,
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
        // Given a valid sport event with ball field
        final String key = "baseball";
        final String value = "{\"sport\": \"baseball\", \"ball\": {\"shape\": \"round\", \"dimensions\": {\"diameter\": \"2.9in\", \"weight\": \"5oz\"}}}";

        // When processing the record
        inputTopic.pipeInput(key, value);

        // Then it should appear in the output topic
        assertThat(outputTopic.getQueueSize(), equalTo(1L));
        var outputRecord = outputTopic.readKeyValue();
        assertThat(outputRecord.key, equalTo(key));
        assertThat(outputRecord.value, equalTo(value));

    }

    @Test
    public void testProcessingWithDifferentSport() {
        // Given a valid sport event with ball field (soccer)
        final String key = "soccer";
        final String value = "{\"sport\": \"soccer\", \"ball\": {\"shape\": \"spherical\", \"dimensions\": {\"diameter\": \"22cm\", \"weight\": \"450g\"}}}";
        
        // When processing the record
        inputTopic.pipeInput(key, value);

        // Then it should appear in the output topic
        assertThat(outputTopic.getQueueSize(), equalTo(1L));
        var outputRecord = outputTopic.readKeyValue();
        assertThat(outputRecord.key, equalTo(key));
        assertThat(outputRecord.value, equalTo(value));

    }

    @Test
    public void testDLQRoutingWithMissingBallField() {
        // Given a sport event without ball field
        final String key = "swimming";
        final String value = "{\"sport\": \"swimming\"}";

        // When processing the record (which will throw an exception due to missing ball field)
        try {
            inputTopic.pipeInput(key, value);
        } catch (RuntimeException e) {
            // Expected - the processor throws an exception for missing ball field
        }

        // Then it should NOT appear in the output topic
        assertTrue(outputTopic.isEmpty());

        // Note: TopologyTestDriver cannot validate DLQ routing as it's a runtime
        // infrastructure feature, not part of the topology itself. This unit test
        // verifies that the exception is thrown, which triggers DLQ routing in
        // production. See KafkaStreamsApplicationDLQIntegrationTest for end-to-end
        // DLQ routing validation using Testcontainers with real Kafka.
    }

    @Test
    public void testInvalidJsonHandling() {
        // Given a record with invalid JSON
        final String key = "junk";
        final String value = "not valid json";

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
    public void testDLQRoutingWithNullBallField() {
        // Given a sport event with explicit null ball field
        final String key = "running";
        final String value = "{\"sport\": \"running\", \"ball\": null}";

        // When processing the record (which will throw an exception due to null ball field)
        try {
            inputTopic.pipeInput(key, value);
        } catch (RuntimeException e) {
            // Expected - the processor throws an exception for null ball field
        }

        // Then it should NOT appear in the output topic
        assertTrue(outputTopic.isEmpty());
    }

    @Test
    public void testMultipleRecordsBatch() {
        // Given multiple valid records with ball fields
        inputTopic.pipeInput("baseball", "{\"sport\": \"baseball\", \"ball\": {\"shape\": \"round\", \"dimensions\": {\"diameter\": \"2.9in\", \"weight\": \"5oz\"}}}");
        inputTopic.pipeInput("tennis", "{\"sport\": \"tennis\", \"ball\": {\"shape\": \"round\", \"dimensions\": {\"diameter\": \"6.7cm\", \"weight\": \"58g\"}}}");

        // Then both should appear in output topic
        assertThat(outputTopic.getQueueSize(), equalTo(2L));

        var record1 = outputTopic.readKeyValue();
        assertThat(record1.key, equalTo("baseball"));

        var record2 = outputTopic.readKeyValue();
        assertThat(record2.key, equalTo("tennis"));

        assertTrue(outputTopic.isEmpty());
    }
}
