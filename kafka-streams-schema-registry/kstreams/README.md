<!-- title: How to build Kafka Streams applications with Schema Registry -->
<!-- description: In this tutorial, learn how to build Kafka Streams applications with Schema Registry, with step-by-step instructions and supporting code. -->

# How to build Kafka Streams applications with Schema Registry

This tutorial demonstrates how to integrate Schema Registry in a Kafka Streams application. That is, how to handle application input and/or output topics that have an associated schema.

The sample application that this tutorial uses is based on the tutorial [How to create a Kafka Streams application](https://developer.confluent.io/confluent-tutorials/creating-first-apache-kafka-streams-application/kstreams/), with the following enhancements to integrate Schema Registry:

- The value serde passed to the [`Consumed.with`](https://kafka.apache.org/41/javadoc/org/apache/kafka/streams/kstream/Consumed.html#with(org.apache.kafka.common.serialization.Serde,org.apache.kafka.common.serialization.Serde)) and [`Produced.with`](https://kafka.apache.org/41/javadoc/org/apache/kafka/streams/kstream/Produced.html#with(org.apache.kafka.common.serialization.Serde,org.apache.kafka.common.serialization.Serde)) methods use a [`SpecificAvroSerde`](https://docs.confluent.io/platform/current/streams/developer-guide/datatypes.html#avro) (serde documentation for other schema formats are also available at this link)
- This serde is configured with Schema Registry connection details (the Schema Registry URL and credentials, if applicable)
- The included unit test uses an in-memory [MockSchemaRegistryClient](https://github.com/confluentinc/schema-registry/blob/master/client/src/main/java/io/confluent/kafka/schemaregistry/client/MockSchemaRegistryClient.java) so that a real Schema Registry instance isn't needed for unit testing
- The `build.gradle` file includes dependencies to serialize and deserialize Avro-formatted records: `io.confluent:kafka-streams-avro-serde` and `org.apache.avro:avro`. Other message formats would require similar dependencies, like `io.confluent:kafka-streams-protobuf-serde` for Protobuf support.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Create Confluent Cloud resources

Login to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name kafka-streams-sr-env \
  --kafka-cluster-name kafka-streams-sr-cluster \
  --create-kafka-key \
  --create-sr-key \
  --kafka-java-properties-file ./kafka-streams-schema-registry/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create input
confluent kafka topic create output
```

Start a console producer to produce Avro-formatted records:

```shell
confluent kafka topic produce input \
  --value-format avro \
  --schema ./kafka-streams-schema-registry/kstreams/src/main/avro/my-record.avsc
```

Enter a few lowercase string records:

```plaintext
{"my_string": "hello"}
{"my_string": "world"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew kafka-streams-schema-registry:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd kafka-streams-schema-registry/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/kafka-streams-schema-registry-standalone.jar \
    io.confluent.developer.KafkaStreamsApplication \
    ./src/main/resources/cloud.properties
```

Validate that you see uppercase string Avro records in the `output` topic.

```shell
confluent kafka topic consume output \
  --value-format avro \
  --from-beginning
```

You should see:

```shell
{"my_string":"HELLO"}
{"my_string":"WORLD"}
```

## Clean up

When you are finished, delete the `kafka-streams-sr-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  * Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
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

  Create the input and output topics for the application:

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic input
  kafka-topics --bootstrap-server localhost:9092 --create --topic output
  ```

  Exit the broker container by entering `Ctrl+D`.

  ## Produce messages

  Open a shell in the Schema Registry container:

  ```shell
  docker exec -it schema-registry /bin/bash
  ```

  Start a console producer to produce Avro-formatted records:

  ```shell
  kafka-avro-console-producer \
    --topic input \
    --bootstrap-server broker:29092 \
    --property schema.registry.url="http://localhost:8081" \
    --property value.schema='{ "namespace": "io.confluent.developer.avro", "type": "record", "name": "MyRecord", "fields": [ {"name": "my_string", "type": "string"} ] }'
  ```

  Enter a few lowercase string Avro records:

  ```plaintext
  {"my_string": "hello"}
  {"my_string": "world"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew kafka-streams-schema-registry:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd kafka-streams-schema-registry/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092` and Schema Registry at `http://localhost:8081`:

  ```shell
  java -cp ./build/libs/kafka-streams-schema-registry-standalone.jar \
      io.confluent.developer.KafkaStreamsApplication \
      ./src/main/resources/local.properties
  ```

  Validate that you see uppercase string Avro records in the `output` topic. Open a shell in the Schema Registry container:

  ```shell
  docker exec -it schema-registry /bin/bash
  ```

  Run a console consumer:

  ```shell
  kafka-avro-console-consumer \
    --topic output \
    --bootstrap-server broker:29092 \
    --property schema.registry.url=http://localhost:8081 \
    --from-beginning
  ```

  You should see:

  ```shell
  {"my_string":"HELLO"}
  {"my_string":"WORLD"}
  ```

  ## Clean up

  Stop the Kafka Streams application by entering `Ctrl+C`.
  
  From your local machine, stop the broker and Schema Registry containers. From the top-level `tutorials` directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```
</details>
