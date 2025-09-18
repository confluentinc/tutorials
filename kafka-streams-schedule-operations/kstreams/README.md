<!-- title: How to schedule periodic operations in a Kafka Streams application -->
<!-- description: In this tutorial, learn how to schedule periodic operations in a Kafka Streams application, with step-by-step instructions and supporting code. -->

# How to schedule periodic operations in a Kafka Streams application

You'd like to have some periodic functionality execute in your Kafka Streams application. In this tutorial, you'll learn how to use punctuations in Kafka Streams to execute work at regular intervals.

To schedule operations in Kafka Streams, you'll need to use the [Processor API](https://kafka.apache.org/36/documentation/streams/developer-guide/processor-api.html).  In this example, you will use the [KStream.process](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KStream.html#process-org.apache.kafka.streams.processor.api.ProcessorSupplier-org.apache.kafka.streams.kstream.Named-java.lang.String...-) method which mixes the Processor API into the Kafka Streams DSL.

```java
 final KStream<String, LoginTime> loginTimeStream = builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), loginTimeSerde));

        loginTimeStream
                .process(new PunctationProcessorSupplier(), Named.as("max-login-time-transformer"), LOGIN_TIME_STORE)
                      .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()));
```

When you call the `KStream.process` method, you'll pass in a [ProcessorSupplier](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/ProcessorSupplier.html), which returns your [Processor](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/Processor.html) implementation.

It's in the `Process.init` method where you'll [schedule](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/ProcessingContext.html#schedule-java.time.Duration-org.apache.kafka.streams.processor.PunctuationType-org.apache.kafka.streams.processor.Punctuator-) arbitrary actions with the [ProcessorContext](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/api/ProcessorContext.html)

```java
 public void init(ProcessorContext<String, Long> context) {
    this.context = context;
    this.context.schedule(Duration.ofSeconds(5), PunctuationType.STREAM_TIME, this::streamTimePunctuator);
    this.context.schedule(Duration.ofSeconds(20), PunctuationType.WALL_CLOCK_TIME, this::wallClockTimePunctuator);
}
```
Here you're scheduling two actions, one using stream time and another on wall-clock time.  With stream time, the event timestamps of the incoming records advance
the internal time tracked by Kafka Streams.  Wall-clock time is the system time of the machine running the Kafka Streams application.  Kafka Streams measures wall-clock during its `poll` interval so executing punctuation based on wall-clock time is best effort only. Here the code uses method handles instead of concrete [Punctuator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/Punctuator.html) instances.

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
  --environment-name kafka-streams-schedule-operations-env \
  --kafka-cluster-name kafka-streams-schedule-operations-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./kafka-streams-schedule-operations/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create punctuation-input
confluent kafka topic create punctuation-output
```

Start a console producer:

```shell
confluent kafka topic produce punctuation-input --parse-key --delimiter :
```

Enter a few JSON-formatted login events:

```plaintext
user-1:{"logInTime":5, "userId":"user-1", "appId":"app-1"}
user-2:{"logInTime":5, "userId":"user-2", "appId":"app-1"}
user-1:{"logInTime":5, "userId":"user-1", "appId":"app-1"}
user-3:{"logInTime":5, "userId":"user-3", "appId":"app-1"}
user-2:{"logInTime":5, "userId":"user-2", "appId":"app-1"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew kafka-streams-schedule-operations:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd kafka-streams-schedule-operations/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/kafka-streams-schedule-operations-standalone.jar \
    io.confluent.developer.KafkaStreamsPunctuation \
    ./src/main/resources/cloud.properties
```

Validate that you see a punctuated max login event in the `punctuation-output` topic.

```shell
confluent kafka topic consume punctuation-output -b \
  --print-key --delimiter : --value-format integer
```

You should see output like:

```shell
user-1 @Tue Sep 16 11:36:01 EDT 2025:5
```

## Clean up

When you are finished, delete the `kafka-streams-schedule-operations-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic punctuation-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic punctuation-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic punctuation-input \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted login events:

  ```plaintext
  user-1:{"logInTime":5, "userId":"user-1", "appId":"app-1"}
  user-2:{"logInTime":5, "userId":"user-2", "appId":"app-1"}
  user-1:{"logInTime":5, "userId":"user-1", "appId":"app-1"}
  user-3:{"logInTime":5, "userId":"user-3", "appId":"app-1"}
  user-2:{"logInTime":5, "userId":"user-2", "appId":"app-1"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew kafka-streams-schedule-operations:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd kafka-streams-schedule-operations/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/kafka-streams-schedule-operations-standalone.jar \
      io.confluent.developer.KafkaStreamsPunctuation \
      ./src/main/resources/local.properties
  ```

  Validate that you see a punctuated max login event in the `punctuation-output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic punctuation-output --from-beginning \
    --property "print.key=true" --property "key.separator=:" \
    --property "value.deserializer=org.apache.kafka.common.serialization.IntegerDeserializer"
  ```

  You should see output like:

  ```plaintext
  user-1 @Tue Sep 16 11:36:01 EDT 2025:5
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
