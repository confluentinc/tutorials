package io.confluent.developer;


import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Singleton container manager for Flink Kafka tests. Ensures that Kafka and Schema Registry
 * containers are started only once and shared across all test classes that extend
 * AbstractFlinkKafkaTest. This prevents resource exhaustion when running many tests in parallel.
 */
public final class SharedFlinkKafkaContainers {

  private static volatile SharedFlinkKafkaContainers instance;
  private static final Object lock = new Object();

  private final ConfluentKafkaContainer kafka;
  private final GenericContainer<?> schemaRegistry;
  private final Integer kafkaPort;
  private final Integer schemaRegistryPort;

  private SharedFlinkKafkaContainers() {
    Network network = Network.newNetwork();

    kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.1.1"))
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_NUM_PARTITIONS", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "500")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withReuse(true)
            .withNetwork(network);
    kafka.start();
    kafkaPort = kafka.getMappedPort(9092);

    schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:8.1.1"))
            .dependsOn(kafka)
            .withExposedPorts(8081)
            .withNetwork(kafka.getNetwork())
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "localhost")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + kafka.getNetworkAliases().get(0) + ":9093")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "PLAINTEXT")
            .withReuse(true)
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(30)));
    schemaRegistry.start();
    schemaRegistryPort = schemaRegistry.getMappedPort(8081);

    // Register shutdown hook to stop containers when JVM exits
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      schemaRegistry.stop();
      kafka.stop();
    }));
  }

  /**
   * Get the singleton instance of SharedFlinkKafkaContainers. Uses double-checked locking
   * to ensure thread-safe lazy initialization.
   *
   * @return the singleton instance
   */
  public static SharedFlinkKafkaContainers getInstance() {
    if (instance == null) {
      synchronized (lock) {
        if (instance == null) {
          instance = new SharedFlinkKafkaContainers();
        }
      }
    }
    return instance;
  }

  public Integer getKafkaPort() {
    return kafkaPort;
  }

  public Integer getSchemaRegistryPort() {
    return schemaRegistryPort;
  }

  public ConfluentKafkaContainer getKafka() {
    return kafka;
  }

  public GenericContainer<?> getSchemaRegistry() {
    return schemaRegistry;
  }
}
