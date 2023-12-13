package io.confluent.developer;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;

class SchemaRegistryContainer extends GenericContainer<SchemaRegistryContainer> {

    SchemaRegistryContainer(String confluentVersion) {
        super("confluentinc/cp-schema-registry:" + confluentVersion);
        withExposedPorts(8081);
    }

    SchemaRegistryContainer withKafka(KafkaContainer kafka) {
        return withKafka(kafka.getNetwork(), kafka.getNetworkAliases().get(0) + ":9092");
    }

    private SchemaRegistryContainer withKafka(Network network, String bootstrapServers) {
        withNetwork(network);
        withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry");
        withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081");
        withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + bootstrapServers);
        return self();
    }

    String getTarget() {
        StringBuilder sb = new StringBuilder();
        sb.append("http://").append(getHost());
        sb.append(":").append(getMappedPort(8081));
        return sb.toString();
    }

}