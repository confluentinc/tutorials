package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.confluent.developer.avro.MyRecord;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.developer.KafkaStreamsApplication.INPUT_TOPIC;
import static io.confluent.developer.KafkaStreamsApplication.OUTPUT_TOPIC;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaStreamsApplicationTest {

    private static SpecificAvroSerde<MyRecord> makeRecordSerde(
            Properties envProps, SchemaRegistryClient srClient) {

        SpecificAvroSerde<MyRecord> serde = new SpecificAvroSerde<>(srClient);
        serde.configure(Collections.singletonMap(
                "schema.registry.url", envProps.getProperty("schema.registry.url")), false);
        return serde;
    }

    @Test
    @DisplayName("Kafka Streams application test")
    void kafkaStreamsTest() {
        var kafkaStreamsAppInstance = new KafkaStreamsApplication();
        final MockSchemaRegistryClient srClient = new MockSchemaRegistryClient();
        var properties = new Properties();
        properties.put(SCHEMA_REGISTRY_URL_CONFIG, "mock://127.0.0.1:8081");
        final SpecificAvroSerde<MyRecord> myRecordSerde = makeRecordSerde(properties, srClient);

        Topology topology = kafkaStreamsAppInstance.buildTopology(properties, myRecordSerde);
        try(TopologyTestDriver driver = new TopologyTestDriver(topology)) {
            final Serde<String> stringSerde = Serdes.String();
            final TestInputTopic<String, MyRecord> inputTopic = driver.createInputTopic(INPUT_TOPIC, stringSerde.serializer(), myRecordSerde.serializer());
            final TestOutputTopic<String, MyRecord> outputTopic = driver.createOutputTopic(OUTPUT_TOPIC, stringSerde.deserializer(), myRecordSerde.deserializer());

            inputTopic.pipeInput("1", new MyRecord("foo"));
            inputTopic.pipeInput("2", new MyRecord("bar"));

            final List<MyRecord> expectedOutput = Arrays.asList(new MyRecord("FOO"), new MyRecord("BAR"));
            final List<MyRecord> actualOutput = outputTopic.readValuesToList();
            assertEquals(expectedOutput, actualOutput);
        }
    }
}