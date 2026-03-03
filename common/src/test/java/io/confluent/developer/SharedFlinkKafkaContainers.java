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
    System.out.println("SharedFlinkKafkaContainers: Starting container initialization");

    System.out.println("SharedFlinkKafkaContainers: Creating Kafka container");
    // Note: We don't use a custom Network to avoid conflicts when multiple JVM processes
    // try to create networks with the same name. Kafka container creates its own network.
    kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.1.1"))
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_NUM_PARTITIONS", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "500")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withLabel("flink-test-containers", "true")  // Add label for cleanup
            .withReuse(true);

    System.out.println("SharedFlinkKafkaContainers: Starting Kafka container");
    kafka.start();
    kafkaPort = kafka.getMappedPort(9092);
    System.out.println("SharedFlinkKafkaContainers: Kafka started on port " + kafkaPort);

    System.out.println("SharedFlinkKafkaContainers: Creating Schema Registry container");
    schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:8.1.1"))
            .dependsOn(kafka)
            .withExposedPorts(8081)
            .withNetwork(kafka.getNetwork())
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "localhost")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + kafka.getNetworkAliases().get(0) + ":9093")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "PLAINTEXT")
            .withLabel("flink-test-containers", "true")  // Add label for cleanup
            .withReuse(true)
            .waitingFor(Wait.forHttp("/subjects")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(60)));  // Increased from 30s

    System.out.println("SharedFlinkKafkaContainers: Starting Schema Registry container");
    schemaRegistry.start();
    schemaRegistryPort = schemaRegistry.getMappedPort(8081);
    System.out.println("SharedFlinkKafkaContainers: Schema Registry started on port " + schemaRegistryPort);

    System.out.println("SharedFlinkKafkaContainers: All containers initialized successfully");

    // Note: With .withReuse(true), we intentionally do NOT add a shutdown hook to stop
    // containers. Testcontainers will manage the lifecycle - containers remain running
    // after tests complete and are available for reuse by subsequent test runs.
    // Ryuk (Testcontainers' resource reaper) will clean them up when appropriate.
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
