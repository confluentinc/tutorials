<!-- title: How to create a Kafka Streams application -->
<!-- description: In this tutorial, learn how to create a Kafka Streams application, with step-by-step instructions and supporting code. -->

# How to create a Kafka Streams application

This tutorial demonstrates how to build a simple Kafka Streams application. You can go more in depth in the [Kafka Streams 101 course.](https://developer.confluent.io/learn-kafka/kafka-streams/get-started/)

In its simplest form, a Kafka Streams application defines a source node for consuming records from a topic, performs one or more operations or transformations on the incoming records, then produces the updated results back to Kafka.  For example, let's work through the following Kafka Streams topology definition that simply uppercases the string values from a source topic.

```java
         builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
                .mapValues(s -> s.toUpperCase())
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));
```

Let's do a quick review of this simple application.

```java
  builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
```
 
This line creates a `KStream` instance using the topic `INPUT_TOPIC` as the source and uses the `Consumed` configuration object to provide the `Serde` objects needed to deserialize the incoming records.

```java
 .mapValues(s -> s.toUpperCase())
```

Here you're performing a basic transformation on the incoming values by uppercasing each one.  
Note that with the Kafka Streams DSL you can use the fluent interface approach, chaining method calls together.

```java
.to(OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));
```
 
After the `mapValues` operation you're producing the transformed values back to Kafka. You'll see the `Produced` configuration object which provides the `Serde` objects Kafka Streams uses to serialize the records.

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
  --environment-name kafka-streams-first-app-env \
  --kafka-cluster-name kafka-streams-first-app-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./creating-first-apache-kafka-streams-application/kstreams/src/main/resources/cloud.properties
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

Enter a few lowercase strings:

```plaintext
hello world
all
streams
lead
to
Kafka
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew creating-first-apache-kafka-streams-application:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd creating-first-apache-kafka-streams-application/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/creating-first-apache-kafka-streams-application-standalone.jar \
    io.confluent.developer.KafkaStreamsApplication \
    ./src/main/resources/cloud.properties
```

Validate that you see uppercase strings in the `output` topic.

```shell
confluent kafka topic consume output -b
```

You should see:

```shell
HELLO WORLD
ALL
STREAMS
LEAD
TO
KAFKA
```

## Clean up

When you are finished, delete the `kafka-streams-first-app-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic input
  kafka-topics --bootstrap-server localhost:9092 --create --topic output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic input
  ```

  Enter a few lowercase strings:

  ```plaintext
  hello world
  all
  streams
  lead
  to
  Kafka
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew creating-first-apache-kafka-streams-application:kstreams/shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd creating-first-apache-kafka-streams-application/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/creating-first-apache-kafka-streams-application-standalone.jar \
      io.confluent.developer.KafkaStreamsApplication \
      ./src/main/resources/local.properties
  ```

  Validate that you see uppercase strings in the `output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic output --from-beginning
  ```

  You should see:

  ```shell
  HELLO WORLD
  ALL
  STREAMS
  LEAD
  TO
  KAFKA
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
