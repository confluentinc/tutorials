<!-- title: How to reset a Kafka Streams application to reprocess input topics -->
<!-- description: In this tutorial, learn how to reset a Kafka Streams application to reprocess input topics, with step-by-step instructions and supporting code. -->

# How to reset a Kafka Streams application to reprocess input topics

This tutorial demonstrates how to use the [Kafka Streams application reset tool](https://docs.confluent.io/platform/current/streams/developer-guide/app-reset-tool.html) in order to reprocess data. Resetting an application can be helpful during development and testing, or when fixing bugs.

The focus here is on the tool itself, rather than the application logic, which simply reads strings from an input topic, uppercases them, and writes the results to an output topic.

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

### Create Confluent Cloud resources

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
  --environment-name kafka-streams-application-reset-env \
  --kafka-cluster-name kafka-streams-application-reset-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./kafka-streams-application-reset/kstreams/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create input
confluent kafka topic create output
```

Start a console producer:

```shell
confluent kafka topic produce input
```

Enter a few strings that include lowercase letters:

```plaintext
foo
bar
baz
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew kafka-streams-application-reset:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd kafka-streams-application-reset/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/kafka-streams-application-standalone.jar \
    io.confluent.developer.KafkaStreamsApplication \
    ./cloud.properties
```

You should see log output showing that strings are being uppercased:

```plaintext
INFO io.confluent.developer.KafkaStreamsApplication - Observed event: bar
INFO io.confluent.developer.KafkaStreamsApplication - Transformed event: BAR
```

As an additional sanity check, validate that you see the uppercased strings `FOO`, `BAR`, and `BAZ` in the `output` topic:

```shell
confluent kafka topic consume output -b
```

## Rerun the application and validate no reprocessing

Enter `Ctrl+C` in the terminal where the Kafka Streams application is running. Then run it again:

```shell
java -cp ./build/libs/kafka-streams-application-standalone.jar \
    io.confluent.developer.KafkaStreamsApplication \
    ./cloud.properties
```

Followed by:

```shell
confluent kafka topic consume output -b
```

Validate that the same three strings are output, showing that no messages in the `input` topic were reprocessed.

## Reset the application

Run the application reset tool that you downloaded earlier. Note that you will run `<KAFKA_HOME>/bin/kafka-streams-application-reset.sh` if you downloaded Apache Kafka, and `<CONFLUENT_HOME>/bin/kafka-streams-application-reset` if you downloaded Confluent platform. You will need to copy the bootstrap servers endpoint from the `./cloud.properties` file and pass it in place of the `<BOOTSTRAP_SERVER>` placeholder:

```shell
<KAFKA_HOME>/bin/kafka-streams-application-reset.sh \
    --application-id kafka-streams-application \
    --input-topics input \
    --config-file ./cloud.properties \
    --bootstrap-server <BOOTSTRAP_SERVER>
``` 

You will see output like this showing that the `input` topic offsets were reset back to zero:

```plaintext
Reset-offsets for input topics [input]
Following input topics offsets will be reset to (for consumer group kafka-streams-application)
Topic: input Partition: 0 Offset: 0
Topic: input Partition: 1 Offset: 0
Topic: input Partition: 2 Offset: 0
Topic: input Partition: 3 Offset: 0
Topic: input Partition: 4 Offset: 0
Topic: input Partition: 5 Offset: 0
Done.
```

## Rerun the application and validate no reprocessing

Enter `Ctrl+C` in the terminal where the Kafka Streams application is running. Then run it once more:

```shell
java -cp ./build/libs/kafka-streams-application-standalone.jar \
    io.confluent.developer.KafkaStreamsApplication \
    ./cloud.properties
```

Followed by:

```shell
confluent kafka topic consume output -b
```

You should now see each uppercase string twice, since the input topic was reprocessed from the beginning.

## Clean up

When you are finished, delete the `kafka-streams-application-reset-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```

<details>
  <summary>Docker instructions</summary>

  ### Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  * Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```
  
  ### Start Kafka in Docker

  Start Kafka with the following command run from the top-level `tutorials` repository directory:
  
  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml up -d
  ```
  
  ### Create topics

  Open a shell in the broker container:

  ```shell
  docker exec -it broker /bin/bash
  ```

  Create the input and output topics for the application:
  
  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic input
  kafka-topics --bootstrap-server localhost:9092 --create --topic output
  ```
  
  Start a console producer:
  
  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic input
  ```
  
  Enter a few strings that include lowercase letters:
  
  ```plaintext
  foo
  bar
  baz
  ```
  
  Enter `Ctrl+C` to exit the console producer.
  
  ### Compile and run the application

  On your local machine, compile the app:
  
  ```shell
  ./gradlew kafka-streams-application-reset:kstreams:shadowJar
  ```
  
  Navigate into the application's home directory:
  
  ```shell
  cd kafka-streams-application-reset/kstreams
  ```
  
  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:
  
  ```shell
  java -cp ./build/libs/kafka-streams-application-standalone.jar \
      io.confluent.developer.KafkaStreamsApplication \
      ./local.properties
  ```
  
  You'll see logging like this demonstrating the input string uppercasing:
  
  ```plaintext
  INFO io.confluent.developer.KafkaStreamsApplication - Observed event: foo
  INFO io.confluent.developer.KafkaStreamsApplication - Transformed event: FOO
  ```

  As an additional sanity check, validate that you see the uppercased strings `FOO`, `BAR`, and `BAZ` in the `output` topic. In the broker container shell:
  
  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic output --from-beginning
  ```
  
  ### Rerun the application and validate no reprocessing
  
  Enter `Ctrl+C` in the terminal where the Kafka Streams application is running. Then run it again:
  
  ```shell
  java -cp ./build/libs/kafka-streams-application-standalone.jar \
      io.confluent.developer.KafkaStreamsApplication \
      ./local.properties
  ```
  
  Followed by the same Kafka console consumer command from the broker container shell:
  
  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic output --from-beginning
  ```
  
  Validate that the same three strings are output, showing that no messages in the `input` topic were reprocessed.
  
  ### Reset the application
  
  Run the application reset tool from the broker container shell:
  
  ```shell
  kafka-streams-application-reset --application-id kafka-streams-application \
    --input-topics input \
    --bootstrap-server localhost:9092 \
    --force
  ``` 

  You will see output like this showing that the `input` topic offsets were reset back to zero:

  ```plaintext
  Force deleting all active members in the group: kafka-streams-application
  Reset-offsets for input topics [input]
  Following input topics offsets will be reset to (for consumer group kafka-streams-application)
  Topic: input Partition: 0 Offset: 0
  Done.
  Deleting inferred internal topics []
  Done.
  ```

  ### Rerun the application and validate no reprocessing
  
  Enter `Ctrl+C` in the terminal where the Kafka Streams application is running. Then run it once more:
  
  ```shell
  java -cp ./build/libs/kafka-streams-application-standalone.jar \
      io.confluent.developer.KafkaStreamsApplication \
      ./local.properties
  ```

  Followed by the same Kafka console consumer command from the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic output --from-beginning
  ```

  You should now see each uppercase string twice.

  ### Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
