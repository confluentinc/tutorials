package io.confluent.developer;


import com.google.common.io.Resources;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;


/**
 * Base class for Flink SQL integration tests that use Flink's Kafka connectors. Encapsulates
 * Kafka broker and Schema Registry Testcontainer management and includes utility methods for
 * dynamically configuring Flink SQL Kafka connectors and processing Table API results.
 * <p>
 * Uses SharedFlinkKafkaContainers singleton to share containers across all test classes,
 * preventing resource exhaustion when running many tests in parallel.
 */
public class AbstractFlinkKafkaTest {

    protected static StreamTableEnvironment streamTableEnv;
    protected static Integer schemaRegistryPort, kafkaPort;

    @BeforeClass
    public static void setup() {
        // create Flink table environment that test subclasses will use to execute SQL statements
        Configuration config = new Configuration();
        config.set(RestartStrategyOptions.RESTART_STRATEGY, "none");
        config.set(StateBackendOptions.STATE_BACKEND, "hashmap");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);
        env.setParallelism(1);
        streamTableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.newInstance().inStreamingMode().build());

        // Get shared Kafka and Schema Registry containers. These are singleton instances that are
        // started once and shared across all test classes, preventing resource exhaustion.
        SharedFlinkKafkaContainers containers = SharedFlinkKafkaContainers.getInstance();
        kafkaPort = containers.getKafkaPort();
        schemaRegistryPort = containers.getSchemaRegistryPort();
    }

    /**
     * Given a resource filename and optional Kafka / Schema Registry ports, return the resource
     * file contents as a String with ports substituted for KAFKA_PORT and SCHEMA_REGISTRY_PORT
     * placeholders.
     *
     * @param resourceFileName   the resource file name
     * @param kafkaPort          the port that Kafka broker exposes
     * @param schemaRegistryPort the port that Schema Registry exposes
     * @return resource file contents with port values substituted for placeholders
     * @throws IOException if resource file can't be read
     */
    protected static String getResourceFileContents(
            String resourceFileName,
            Optional<Integer> kafkaPort,
            Optional<Integer> schemaRegistryPort
    ) throws IOException {
        URL url = Resources.getResource(resourceFileName);
        String contents = Resources.toString(url, StandardCharsets.UTF_8);
        if (kafkaPort.isPresent()) {
            contents = contents.replaceAll("KAFKA_PORT", kafkaPort.get().toString());
        }
        if (schemaRegistryPort.isPresent()) {
            contents = contents.replaceAll("SCHEMA_REGISTRY_PORT", schemaRegistryPort.get().toString());
        }
        return contents;
    }

    /**
     * Given a resource filename, return the resource file contents as a String.
     *
     * @param resourceFileName the resource file name
     * @return resource file contents
     * @throws IOException if resource file can't be read
     */
    protected static String getResourceFileContents(
            String resourceFileName
    ) throws IOException {
        // no Kafka / Schema Registry ports
        return getResourceFileContents(resourceFileName, Optional.empty(), Optional.empty());
    }
}
