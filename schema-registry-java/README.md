<!-- title: How to build Kafka client applications in Java with Schema Registry -->
<!-- description: In this tutorial, learn how to build Kafka client applications in Java with Schema Registry, with step-by-step instructions and supporting code. -->

# How to build Kafka client applications in Java with Schema Registry

This tutorial demonstrates how to build Kafka producer and consumer applications in Java that use Schema Registry for message schema management. You'll learn how to configure your Java applications to serialize and deserialize records, ensuring type safety and schema evolution compatibility. By the end of this tutorial, you'll have working applications that produce and consume device temperature reading records.

The applications in this tutorial use Avro-formatted messages. In order to use Protobuf or JSON Schema formatting, you would need to use a [different serializer / deserializer](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html), but otherwise the applications would be similarly structured.

The steps in this tutorial outline how to set up the required Kafka infrastructure and run the provided producer / consumer applications. For a deeper look at the application source code, refer to the `Code explanation` section at the bottom.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the Docker instructions section at the bottom.

## Prerequisites

- Java 17
- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Create Confluent Cloud resources

Log in to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial.

Note: You may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name kafka-sr-env \
  --kafka-cluster-name kafka-sr-cluster \
  --create-kafka-key \
  --create-sr-key \
  --kafka-java-properties-file ./schema-registry-java/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the topic for the application:

```shell
confluent kafka topic create readings
```

## Compile and run the producer application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew schema-registry-java:shadowJar
```

Navigate into the application's home directory:

```shell
cd schema-registry-java
```

Run the producer application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/schema-registry-java-standalone.jar \
    io.confluent.developer.AvroProducer \
    ./src/main/resources/cloud.properties
```

Validate that you see temperature reading Avro records in the `readings` topic.

```shell
confluent kafka topic consume readings \
  --value-format avro \
  --from-beginning
```

You should see output similar to the following:

```shell
{"deviceId":"3","temperature":99.5231}
{"deviceId":"3","temperature":70.56588}
{"deviceId":"1","temperature":99.817894}
{"deviceId":"1","temperature":98.89636}
{"deviceId":"0","temperature":96.56193}
{"deviceId":"2","temperature":97.53318}
{"deviceId":"2","temperature":75.94116}
{"deviceId":"0","temperature":74.87793}
{"deviceId":"0","temperature":76.37975}
{"deviceId":"0","temperature":83.31611}
```

These messages correspond to the Avro schema in `src/main/avro/temp-reading.avsc`:

```json
{
  "namespace": "io.confluent.developer.avro",
  "type": "record",
  "name": "TempReading",
  "fields": [
    { "name": "deviceId", "type": "string" },
    { "name": "temperature", "type": "float" }
  ]
}
```

## Run the consumer application

Run the consumer application:

```shell
java -cp ./build/libs/schema-registry-java-standalone.jar \
    io.confluent.developer.AvroConsumer \
    ./src/main/resources/cloud.properties
```

You should see output similar to the following:

```shell
[main] INFO - Consumed event: key = 4, value = {"deviceId": "4", "temperature": 99.00065}
[main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 95.12411}
[main] INFO - Consumed event: key = 0, value = {"deviceId": "0", "temperature": 99.8184}
[main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 92.55404}
[main] INFO - Consumed event: key = 3, value = {"deviceId": "3", "temperature": 79.467354}
[main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 77.81964}
[main] INFO - Consumed event: key = 1, value = {"deviceId": "1", "temperature": 87.234375}
[main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 78.16981}
[main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 97.42639}
[main] INFO - Consumed event: key = 1, value = {"deviceId": "1", "temperature": 98.66289}
```

## Clean up

When you are finished, delete the `kafka-sr-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```

<details>
  <summary>Docker instructions</summary>

  ## Prerequisites

  - Java 17
  - Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  - [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  - Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

  ## Start Kafka in Docker

  Start Kafka and Schema Registry with the following command run from the top-level `tutorials` repository directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml up -d
  ```

  ## Create topics

  Open a shell in the broker container:

  ```shell
  docker exec -it broker /bin/bash
  ```

  Create the topic for the application:

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic readings
  ```

  Exit the broker container by entering `Ctrl+D`.

  ## Compile and run the producer application

  On your local machine, compile the app:

  ```shell
  ./gradlew schema-registry-java:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd schema-registry-java
  ```

  Run the producer application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092` and Schema Registry at `http://localhost:8081`:

  ```shell
  java -cp ./build/libs/schema-registry-java-standalone.jar \
      io.confluent.developer.AvroProducer \
      ./src/main/resources/local.properties
  ```

  Validate that you see temperature reading Avro records in the `readings` topic. Open a shell in the Schema Registry container:

  ```shell
  docker exec -it schema-registry /bin/bash
  ```

  Run a console consumer:

  ```shell
  kafka-avro-console-consumer \
    --topic readings \
    --bootstrap-server broker:29092 \
    --property schema.registry.url=http://localhost:8081 \
    --from-beginning
  ```

  You should see output similar to this:

  ```shell
  {"deviceId":"2","temperature":96.71052551269531}
  {"deviceId":"2","temperature":78.42681121826172}
  {"deviceId":"3","temperature":95.85462951660156}
  {"deviceId":"2","temperature":83.17869567871094}
  {"deviceId":"3","temperature":79.87565612792969}
  {"deviceId":"1","temperature":79.03103637695312}
  {"deviceId":"0","temperature":87.11306762695312}
  {"deviceId":"0","temperature":76.37906646728516}
  {"deviceId":"3","temperature":75.17118072509766}
  {"deviceId":"2","temperature":84.00798034667969}
  ```

  These messages correspond to the Avro schema in `src/main/avro/temp-reading.avsc`:

  ```json
  {
    "namespace": "io.confluent.developer.avro",
    "type": "record",
    "name": "TempReading",
    "fields": [
      { "name": "deviceId", "type": "string" },
      { "name": "temperature", "type": "float" }
    ]
  }
  ```

  ## Run the consumer application

  Run the consumer application:

  ```shell
  java -cp ./build/libs/schema-registry-java-standalone.jar \
      io.confluent.developer.AvroConsumer \
      ./src/main/resources/local.properties
  ```

  You should see output similar to the following:

  ```shell
  [main] INFO - Consumed event: key = 4, value = {"deviceId": "4", "temperature": 99.00065}
  [main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 95.12411}
  [main] INFO - Consumed event: key = 0, value = {"deviceId": "0", "temperature": 99.8184}
  [main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 92.55404}
  [main] INFO - Consumed event: key = 3, value = {"deviceId": "3", "temperature": 79.467354}
  [main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 77.81964}
  [main] INFO - Consumed event: key = 1, value = {"deviceId": "1", "temperature": 87.234375}
  [main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 78.16981}
  [main] INFO - Consumed event: key = 2, value = {"deviceId": "2", "temperature": 97.42639}
  [main] INFO - Consumed event: key = 1, value = {"deviceId": "1", "temperature": 98.66289}
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers. From the top-level `tutorials` directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```

</details>

<details>
  <summary>Code explanation</summary>

  This section summarizes the application source files in `src/main/java/io/confluent/developer/`.

  ### AvroProducer.java

  The `AvroProducer` class demonstrates how to produce Avro-encoded messages to a Kafka topic using Schema Registry. The producer:

  1. **Loads configuration**: Reads Kafka and Schema Registry connection properties from a file.

  2. **Configures serializers**: Sets the key serializer to `StringSerializer` and the value serializer to `KafkaAvroSerializer`. The `KafkaAvroSerializer` automatically registers the Avro schema with Schema Registry on first use and embeds a schema ID in each message.

  3. **Creates producer instance**: Instantiates a `KafkaProducer` parameterized with `String` keys and `TempReading` (the generated Avro class) values.

  4. **Generates and sends records**: Creates random temperature readings corresponding to random device IDs and produces messages to the `readings` topic.

  5. **Cleans up**: Flushes any pending records and closes the producer to ensure all messages are sent before the application terminates.

  ### AvroConsumer.java

  The `AvroConsumer` class demonstrates how to consume Avro-encoded messages from a Kafka topic using Schema Registry. The consumer:

  1. **Loads configuration**: Reads Kafka and Schema Registry connection properties from a properties file.

  2. **Configures deserializers**: Sets the key deserializer to `StringDeserializer` and the value deserializer to `KafkaAvroDeserializer`. The `KafkaAvroDeserializer` automatically retrieves the schema from Schema Registry using the schema ID embedded in each message.

  3. **Sets consumer properties**:
     - `AUTO_OFFSET_RESET_CONFIG` is set to `"earliest"` to read from the beginning of the topic
     - `GROUP_ID_CONFIG` is set to `"avro-consumer-group"` to identify this consumer group
     - `SPECIFIC_AVRO_READER_CONFIG` is set to `true` to deserialize messages into the specific `TempReading` class rather than a generic `GenericRecord`. See [here](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/serdes-avro.html#avro-deserializer) for details.

  4. **Subscribes and polls**: Subscribes to the `readings` topic and enters a polling loop that continuously fetches records in batches. Each consumed record is logged with its key and deserialized `TempReading` value.

</details>
