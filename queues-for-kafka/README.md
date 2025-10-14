<!-- title: How to scale Kafka consumption throughput with share consumers (KIP-932: Queues for Kafka) -->
<!-- description: In this tutorial, learn how to scale Kafka consumption throughput with share consumers (from KIP-932: Queues for Kafka), with step-by-step instructions and supporting code. -->

# How to scale Kafka consumption throughput with share consumers (KIP-932: Queues for Kafka)

This tutorial demonstrates how to produce a high volume of messages to Kafka, and then compare consumption throughput when using both regular consumers and share consumers. The steps in this tutorial outline how to set up a cluster for share consumers, run the provided producer / consumer applications, and compare performance results between classic Kafka consumer instances and share consumers. For a deeper look at the application source code, refer to the `Code explanation` section at the bottom.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the `Docker instructions` section at the bottom.

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- [Apache Kafka 4.1](https://kafka.apache.org/downloads) for its command-line tools
- Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Create Confluent Cloud resources

First, create a Dedicated 1-CKU cluster in Confluent Cloud by following the instructions [here](https://docs.confluent.io/cloud/current/clusters/create-cluster.html#create-ak-clusters).

Since Queues for Kafka is currently a Closed Preview feature, you'll need to open a support request to enable the feature on your cluster. In the [Confluent Support Portal](https://support.confluent.io/), open a ticket requesting that Queues for Kafka be enabled for your cluster. Provide the cluster ID in your request, which you can find in the [Confluent Cloud Console](https://confluent.cloud/) by navigating to `Cluster Settings` from your Dedicated cluster overview page.

## Confluent CLI setup

Run the following series of commands to log in and set the active Confluent Cloud environment and cluster.

```shell
confluent login --prompt --save
confluent environment list
confluent environment use <ENVIRONMENT_ID>
confluent kafka cluster list
confluent kafka cluster use <CLUSTER_ID>
```

## Generate Confluent Cloud credentials

Generate a Kafka API key by substituting the cluster ID from the previous command:

```shell
confluent api-key create --resource <CLUSTER_ID>
```

Copy the API key into the file `queues-for-kafka/src/main/resources/cloud.properties` where you see the `<API_KEY>` placeholder, and copy the API secret where you see the `<API_SECRET>` placeholder.

Run this command to get your cluster's bootstrap servers endpoint:

```shell
confluent kafka cluster describe
```

Copy the endpoint (of the form `pkc-<ID>.<REGION>.<CLOUD>.confluent.cloud:9092`) into the same `cloud.properties` file where you see the `<BOOTSTRAP_SERVERS>` placeholder. Do not copy the leading `SASL_SSL://`.

## Create topic

Create a 6-partition topic called `strings` that we will use to test consumption throughput.

```shell
confluent kafka topic create strings --partitions 6
```

## Compile and run the producer application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew queues-for-kafka:shadowJar
```

Navigate into the application's home directory:

```shell
cd queues-for-kafka
```

Run the producer application, passing the `cloud.properties` Kafka client configuration file that you populated with your Dedicated cluster's bootstrap servers endpoint and credentials:

```shell
java -cp ./build/libs/kafka-consumer-comparison-app-0.0.1.jar \
  io.confluent.developer.ProducerApp \
  --properties-file ./src/main/resources/cloud.properties
```

## Run consumer applications

In a separate shell, run the regular `KafkaConsumer`-based application. This will run 16 concurrent consumers. Only 6 will actively consume since a partition can only be assigned to one consumer instance. It will simulate a 500-millisecond workload and report throughput after consuming 1,000 events.

```shell
java -cp ./build/libs/kafka-consumer-comparison-app-0.0.1.jar \
  io.confluent.developer.ConsumerApp \
  --properties-file ./src/main/resources/cloud.properties \
  --consumer-type consumer \
  --num-consumers 16 \
  --wait-ms 500 \
  --total-events 1000
```

The app will exit once 1,000 events have been consumed, which should take around a minute and a half. You will see a log message like this reporting the duration:

```plaintext
Completed consuming 1000 messages in 89.61 seconds.
```

Next, run the consumer application using share consumers.

First, alter the `share-consumer-group` to begin consuming from the earliest offset:

```shell
<KAFKA_HOME>/bin/kafka-configs.sh --bootstrap-server <BOOTSTRAP_SERVER> \
  --group share-consumer-group --alter --add-config 'share.auto.offset.reset=earliest' \
  --command-config ./src/main/resources/cloud.properties
```

Run the consumer app again using the same number of threads and simulated event processing time, except this time pass the `share_consumer` consumer type:

```shell
java -cp ./build/libs/kafka-consumer-comparison-app-0.0.1.jar \
  io.confluent.developer.ConsumerApp \
  --properties-file ./src/main/resources/cloud.properties \
  --consumer-type share_consumer \
  --num-consumers 16 \
  --wait-ms 500 \
  --total-events 1000
```

This time, the app should take closer to 30 seconds to complete, given that consumption scales to all 16 threads. You will see a log message like this reporting the duration:

```plaintext
Completed consuming 1000 messages in 31.42 seconds.
```

## Other suggested experiments

Try different application configurations to see how consumption throughput is impacted. For example, vary `--num-consumers` and `--wait-ms` to see how throughput scales with more workers and different per-event wait times. Also try a different number of topic partitions. How does it impact consumption throughput?

## Clean up

When you are finished, delete the Confluent Cloud resources created for this tutorial. For example, if you are using an isolated environment, delete it by first getting the environment ID in the form `env-123456`:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

<details>
  <summary>Docker instructions</summary>

  ## Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  * Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

  ## Start Kafka in Docker

  Start Apache Kafka 4.1 with the following command:

  ```shell
  docker compose -f ./queues-for-kafka/docker-compose.yml up -d
  ```

  ## Enable share consumption

  Open a shell in the broker container:

  ```shell
  docker exec --workdir /opt/kafka/bin/ -it broker /bin/bash
  ```

  Enable share consumers:

  ```shell
  ./kafka-features.sh --bootstrap-server localhost:9092 upgrade --feature share.version=1
  ```

  Alter the `share-consumer-group` share group to begin consuming from the earliest offset:

  ```shell
  ./kafka-configs.sh --bootstrap-server localhost:9092 \
    --group share-consumer-group --alter \
    --add-config 'share.auto.offset.reset=earliest'
  ```

  ## Create topics

  In the broker container, create a topic called `strings` with 6 partitions:

  ```shell
  ./kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --partitions 6 --topic strings
  ```

  Enter `Ctrl+D` to exit the container shell.

  ## Compile and run the producer application

  On your local machine, compile the app:

  ```shell
  ./gradlew queues-for-kafka:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd queues-for-kafka
  ```

  Run the producer application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/kafka-consumer-comparison-app-0.0.1.jar \
      io.confluent.developer.ProducerApp \
      --properties-file ./src/main/resources/local.properties
  ```

  ## Run consumer applications

  In a separate shell, run the regular `KafkaConsumer`-based application. This will run 16 concurrent consumers. Only 6 will actively consume since a partition can only be assigned to one consumer instance. It will simulate a 500-millisecond workload and report throughput after consuming 1,000 events.

  ```shell
  java -cp ./build/libs/kafka-consumer-comparison-app-0.0.1.jar \
      io.confluent.developer.ConsumerApp \
      --properties-file ./src/main/resources/local.properties \
      --consumer-type consumer \
      --num-consumers 16 \
      --wait-ms 500 \
      --total-events 1000
  ```

  The app will exit once 1,000 events have been consumed, which should take around a minute and a half. You will see a log message like this reporting the duration:

  ```plaintext
  Completed consuming 1000 messages in 89.61 seconds.
  ```

  Next, run the consumer app again using the same number of threads and simulated event processing time, except this time pass the `share_consumer` consumer type:

  ```shell
  java -cp ./build/libs/kafka-consumer-comparison-app-0.0.1.jar \
      io.confluent.developer.ConsumerApp \
      --properties-file ./src/main/resources/local.properties \
      --consumer-type share_consumer \
      --num-consumers 16 \
      --wait-ms 500 \
      --total-events 1000
  ```

  This time, the app should take closer to 30 seconds to complete, given that consumption scales to all 16 threads. You will see a log message like this reporting the duration:

  ```plaintext
  Completed consuming 1000 messages in 31.42 seconds.
  ```

  ## Other suggested experiments

  Try different application configurations to see how consumption throughput is impacted. For example, vary `--num-consumers` and `--wait-ms` to see how throughput scales with more workers and different per-event wait times. Also try a different number of topic partitions. How does it impact consumption throughput?

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./queues-for-kafka/docker-compose.yml down
  ```

</details>

<details>
  <summary>Code explanation</summary>

  This section summarizes the key application source files under `src/main/java/io/confluent/developer`.

  - **`ProducerApp.java`**: Standalone producer that sends a high volume of string messages to the `strings` topic.

      - Parses CLI options via `ProducerAppArgParser` to locate the Kafka client properties file.
      - Builds a `KafkaProducer<String, String>` with `StringSerializer` for keys/values and `acks=all` for durability.
      - Produces 1,000,000 messages (key and value are the stringified index), logs progress every 10,000, and throttles briefly to keep the producer running longer and avoid overwhelming the broker.
      - The producer flushes every event so that there aren't large batches. This ensures that each multiple share consumers will be able to actively consume from a given partition.

  - **`ConsumerApp.java`**: Orchestrates multi-threaded consumption to compare regular `KafkaConsumer` vs `KafkaShareConsumer`-based throughput.

      - Parses CLI options via `ConsumerAppArgParser`:
          - `--consumer-type` selects `consumer` (regular) or `share_consumer` (share consumer).
          - `--num-consumers` controls the number of consumer worker threads.
          - `--wait-ms` simulates per-record processing time (sleep per event).
          - `--total-events` stops after consuming the specified number of events across all workers.
      - Builds consumer properties common to both implementations: `StringDeserializer` for keys/values and the appropriate group.
          - For regular consumers: sets `group.id=consumer-group` and `auto.offset.reset=earliest`.
          - For share consumers: sets `group.id=share-consumer-group`, `share.acknowledgement.mode=explicit`, and `max.poll.records=100`.
      - Creates an `ExecutorService` and launches N `ConsumerThread` workers with an event handler that:
          - Sleeps for `--wait-ms` to simulate work.
          - Atomically counts records across all workers, logs progress periodically, and records total elapsed time when `--total-events` is reached.
      - Adds a shutdown hook to close all consumers and the executor service cleanly.

  - **`ConsumerThread.java`**: A runnable worker used by `ConsumerApp` that encapsulates the consumption loop for either consumer type.

      - Two constructors: one for a `KafkaConsumer` and one for a `KafkaShareConsumer`. Each subscribes the consumer to the `strings` topic.
      - In `run()`, polls for events and invokes the provided `EventHandler` per record.
          - Share consumer path: after handling a record, explicitly acknowledges with `AcknowledgeType.ACCEPT` or `REJECT` on error.
          - Regular consumer path: handles records without explicit acknowledgements (normal consumer semantics).
      - Cleanly closes the underlying consumers and exits once the desired number of events is consumed.

  - **`ConsumerAppArgParser.java`**: Command-line parsing and validation for the consumer app using Apache Commons CLI.

      - Options: `--properties-file`, `--consumer-type`, `--num-consumers`, `--wait-ms`, and `--total-events`.
      - Validates ranges (e.g., consumers 1–16, wait 1–5000 ms, events 1–1,000,000).

  - **`ProducerAppArgParser.java`**: Minimal command-line parsing for the producer app.
      - Option: `--properties-file` to locate the Kafka client configuration.

  ### Notes

  - All examples use the topic `strings`. Adjust the topic name in the source if needed.
  - Kafka client configuration is provided via the properties files in `src/main/resources` (`cloud.properties` or `local.properties`).

</details>
