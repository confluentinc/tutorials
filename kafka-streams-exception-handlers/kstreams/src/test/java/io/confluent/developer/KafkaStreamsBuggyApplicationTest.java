package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;


class KafkaStreamsBuggyApplicationTest {

    @Test
    @DisplayName("Test that continuing exception handlers work as expected despite random exceptions")
    void testExceptionHandlers() {
        var kafkaStreamsAppInstance = new KafkaStreamsBuggyApplication();

        // Exception handlers
        var properties = new Properties();
        properties.put(StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                ContinuingDeserializationExceptionHandler.class.getCanonicalName());
        properties.put(StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG,
                ContinuingProcessingExceptionHandler.class.getCanonicalName());
        properties.put(StreamsConfig.PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
                ContinuingProductionExceptionHandler.class.getCanonicalName());

        Topology topology = kafkaStreamsAppInstance.buildTopology(properties);
        try(TopologyTestDriver driver = new TopologyTestDriver(topology, properties)) {
            Serde<String> stringSerde = Serdes.String();
            Serde<Integer> intSerde = Serdes.Integer();

            TestInputTopic<Integer, String> inputTopic = driver.createInputTopic("input-topic",
                    intSerde.serializer(), stringSerde.serializer());
            TestOutputTopic<String, String> outputTopic = driver.createOutputTopic("output-topic",
                    stringSerde.deserializer(), stringSerde.deserializer());

            for (int i = 0; i < 100; i++) {
                inputTopic.pipeInput(i, "foo");
            }

            // topic incorrectly serialized the key as a String, which will trigger a deserialization exception in the app
            TestInputTopic<String, String> badInputTopic = driver.createInputTopic("input-topic",
                    stringSerde.serializer(), stringSerde.serializer());
            badInputTopic.pipeInput("1", "foo");

            int numRecordProduced = outputTopic.readValuesToList().size();
            assertTrue(numRecordProduced > 0 && numRecordProduced < 100);
        }
    }

}
