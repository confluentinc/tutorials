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

  private static final String KAFKA_NETWORK_ALIAS = "kafka";

  private SharedFlinkKafkaContainers() {
    System.out.println("SharedFlinkKafkaContainers: Starting container initialization");

    // Create an explicit network for both containers to share
    // Docker will generate a unique name to avoid conflicts between parallel test runs
    System.out.println("SharedFlinkKafkaContainers: Creating shared network");
    Network network = Network.newNetwork();

    System.out.println("SharedFlinkKafkaContainers: Creating Kafka container");
    kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
            .withNetwork(network)  // Set network before setting alias
            .withNetworkAliases(KAFKA_NETWORK_ALIAS)  // Explicit network alias for DNS resolution
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_NUM_PARTITIONS", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "500")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withLabel("flink-test-containers", "true");  // Add label for cleanup

    System.out.println("SharedFlinkKafkaContainers: Starting Kafka container");
    kafka.start();
    kafkaPort = kafka.getMappedPort(9092);
    System.out.println("SharedFlinkKafkaContainers: Kafka started on port " + kafkaPort +
                       ", network alias: " + KAFKA_NETWORK_ALIAS);

    System.out.println("SharedFlinkKafkaContainers: Creating Schema Registry container");
    schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:latest"))
            .dependsOn(kafka)
            .withExposedPorts(8081)
            .withNetwork(network)  // Use the same network as Kafka
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "localhost")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + KAFKA_NETWORK_ALIAS + ":9093")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "PLAINTEXT")
            .withLabel("flink-test-containers", "true")  // Add label for cleanup
            .waitingFor(Wait.forHttp("/subjects")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(60)));  // Increased from 30s

    System.out.println("SharedFlinkKafkaContainers: Starting Schema Registry container");
    schemaRegistry.start();
    schemaRegistryPort = schemaRegistry.getMappedPort(8081);
    System.out.println("SharedFlinkKafkaContainers: Schema Registry started on port " + schemaRegistryPort);

    System.out.println("SharedFlinkKafkaContainers: All containers initialized successfully");

    // Add shutdown hook to ensure containers are stopped when the JVM exits
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("SharedFlinkKafkaContainers: Shutting down containers");
      try {
        if (schemaRegistry != null && schemaRegistry.isRunning()) {
          schemaRegistry.stop();
        }
        if (kafka != null && kafka.isRunning()) {
          kafka.stop();
        }
        System.out.println("SharedFlinkKafkaContainers: Containers stopped successfully");
      } catch (Exception e) {
        System.err.println("SharedFlinkKafkaContainers: Error stopping containers: " + e.getMessage());
      }
    }));
  }

  /**
   * Get the singleton instance of SharedFlinkKafkaContainers. Uses double-checked locking
   * to ensure thread-safe lazy initialization within a JVM. When running across multiple
   * JVM processes (Gradle workers), uses retry logic to handle race conditions during
   * container creation.
   *
   * @return the singleton instance
   */
  public static SharedFlinkKafkaContainers getInstance() {
    if (instance == null) {
      synchronized (lock) {
        if (instance == null) {
          instance = createInstanceWithRetry();
        }
      }
    }
    return instance;
  }

  /**
   * Create instance with retry logic to handle race conditions when multiple Gradle
   * test workers (separate JVMs) start simultaneously. If container creation fails,
   * retry with exponential backoff to allow another worker to succeed first.
   */
  private static SharedFlinkKafkaContainers createInstanceWithRetry() {
    int maxAttempts = 3;
    int baseDelayMs = 5000;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        System.out.println("SharedFlinkKafkaContainers: Attempt " + attempt + " to initialize containers");
        return new SharedFlinkKafkaContainers();
      } catch (Exception e) {
        if (attempt == maxAttempts) {
          System.err.println("SharedFlinkKafkaContainers: Failed to initialize after " + maxAttempts + " attempts");
          throw e;
        }

        // Exponential backoff: 5s, 10s
        int delayMs = baseDelayMs * attempt;
        System.err.println("SharedFlinkKafkaContainers: Initialization failed (attempt " + attempt + "), " +
                          "retrying in " + delayMs + "ms. Error: " + e.getMessage());

        try {
          Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting to retry container creation", ie);
        }
      }
    }

    // Should never reach here
    throw new RuntimeException("Failed to create SharedFlinkKafkaContainers");
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
