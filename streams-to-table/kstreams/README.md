<!-- title: How to convert a KStream to a KTable in Kafka Streams -->
<!-- description: In this tutorial, learn how to convert a `KStream` to a `KTable` in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to convert a KStream to a KTable in Kafka Streams

If you have a KStream and you need to convert it to a KTable, `KStream.toTable` does the trick. Prior to the introduction of this method in Apache Kafka 2.5, a dummy aggregation operation was required.

As a concrete example, consider a topic with string keys and values. To convert the stream to a `KTable`:

``` java
  KTable<String, String> convertedTable = builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
    .toTable(Materialized.as("stream-converted-to-table"));
```

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the `Docker instructions` section at the bottom.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* [Apache Kafka](https://kafka.apache.org/downloads) or [Confluent Platform](https://docs.confluent.io/platform/current/installation/installing_cp/zip-tar.html) (both include the Kafka Streams application reset tool)
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
  --environment-name kafka-streams-stream-to-table-env \
  --kafka-cluster-name kafka-streams-stream-to-table-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./streams-to-table/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create input-topic
confluent kafka topic create streams-output-topic
confluent kafka topic create table-output-topic
```

Start a console producer:

```shell
confluent kafka topic produce input-topic --parse-key --delimiter :
```

Enter a few key/value pairs:

```plaintext
1:one
2:two
3:three
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew streams-to-table:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd streams-to-table/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/streams-to-table-standalone.jar \
    io.confluent.developer.StreamsToTable \
    ./src/main/resources/cloud.properties
```

Validate that you see the same messages in the `streams-output-topic` and `table-output-topic` topics. This is because converting to a `KTable` is a logical operation and only changes the interpretation of the stream.

```shell
confluent kafka topic consume streams-output-topic -b --print-key
```

```shell
confluent kafka topic consume table-output-topic -b --print-key
```

You should see:

```shell
1	one
2	two
3	three
```

## Clean up

When you are finished, delete the `kafka-streams-stream-to-table-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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

  Start Kafka with the following command run from the top-level `tutorials` repository directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml up -d
  ```

  ## Create topics

  Open a shell in the broker container:

  ```shell
  docker exec -it broker /bin/bash
  ```

  Create the input and output topics for the application:

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic input-topic
  kafka-topics --bootstrap-server localhost:9092 --create --topic streams-output-topic
  kafka-topics --bootstrap-server localhost:9092 --create --topic table-output-topic
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic input-topic \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few key/value pairs:

  ```plaintext
  1:one
  2:two
  3:three
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew streams-to-table:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd streams-to-table/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/streams-to-table-standalone.jar \
      io.confluent.developer.StreamsToTable \
      ./src/main/resources/local.properties
  ```

  Validate that you see the same messages in the `streams-output-topic` and `table-output-topic` topics. This is because converting to a `KTable` is a logical operation and only changes the interpretation of the stream.

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic streams-output-topic --from-beginning --property  "print.key=true"
  ```

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic table-output-topic --from-beginning --property  "print.key=true"
  ```

  You should see:

  ```shell
  1	one
  2	two
  3	three
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
