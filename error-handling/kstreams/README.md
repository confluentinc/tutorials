<!-- title: How to handle uncaught exceptions in Kafka Streams -->
<!-- description: In this tutorial, learn how to handle uncaught exceptions in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to handle uncaught exceptions in Kafka Streams

You have an event streaming application, and you want to make sure that it's robust in the face of unexpected errors. Depending on the situation, you'll want the application to either continue running or shut down.  Using an implementation of the [StreamsUncaughtExceptionHandler](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/errors/StreamsUncaughtExceptionHandler.html) can provide this functionality.

To handle uncaught exceptions use the [KafkaStreams.setUncaughtExceptionHandler](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/KafkaStreams.html#setUncaughtExceptionHandler(org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler)) method:

```java
final StreamsUncaughtExceptionHandler exceptionHandler =
        new MaxFailuresUncaughtExceptionHandler(3, 3600000);

kafkaStreams.setUncaughtExceptionHandler(exceptionHandler);
```

You can also use a lambda instead of a concrete implementation:
```java
kafkaStreams.setUncaughtExceptionHander((exception) -> StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD);
```

The `StreamsUncaughtExceptionHandler` interface gives you an opportunity to respond to exceptions not handled by Kafka Streams. It has one method, `handle`, and it returns an enum of type [StreamThreadExceptionResponse](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/errors/StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.html
) which provides you the opportunity to instruct Kafka Streams how to respond to the exception. There are three possible values: `REPLACE_THREAD`, `SHUTDOWN_CLIENT`, or `SHUTDOWN_APPLICATION`.

It's important to note that the exception handler is for errors not related to malformed records as when the error occurs, Kafka Streams will not commit, and when restarting a thread, it will encounter the bad record again. 

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
  --environment-name kafka-streams-error-handling-env \
  --kafka-cluster-name kafka-streams-error-handling-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./error-handling/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create input_topic
confluent kafka topic create output_topic
```

Start a console producer:

```shell
confluent kafka topic produce input_topic
```

Enter a few strings:

```plaintext
apples
pears
tomatoes
bread
butter
milk
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew error-handling:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd error-handling/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/error-handling-standalone.jar \
    io.confluent.developer.StreamsUncaughtExceptionHandling \
    ./src/main/resources/cloud.properties
```

Validate that the application continues to run and that there are uppercase strings in the output topic despite the fact that the application threw `RuntimeException`s.

```shell
confluent kafka topic consume output_topic -b
```

## Clean up

When you are finished, delete the `kafka-streams-error-handling-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic input_topic
  kafka-topics --bootstrap-server localhost:9092 --create --topic output_topic
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic input_topic
  ```

  Enter a few strings:

  ```plaintext
  apples
  pears
  tomatoes
  bread
  butter
  milk
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew error-handling:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd error-handling/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/error-handling-standalone.jar \
      io.confluent.developer.StreamsUncaughtExceptionHandling \
      ./src/main/resources/local.properties
  ```

  Validate that the application continues to run and that there are uppercase strings in the output topic despite the fact that the application threw `RuntimeException`s.

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic output_topic --from-beginning
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
