<!-- title: How to emit only the final result of a windowed aggregation in Kafka Streams -->
<!-- description: In this tutorial, learn how to emit only the final result of a windowed aggregation in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to emit only the final result of a windowed aggregation in Kafka Streams

A windowed aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key over a specified time window. A windowed aggregation will generally return intermediate results while a window is still
open, but sometimes you may only care about the final result for a given window. In this tutorial, we take an input topic of
sensor pressure readings and output _only_ the final count of readings per sensor for each time window.

The key to emitting only final results is in the call to `KTable.suppress` in the following snippet that builds the topology:
``` java annotate
 builder.stream(INPUT_TOPIC, consumedPressure)
                .selectKey((key, value) -> value.id())
                .groupByKey(groupedPressure)
                .windowedBy(windows)
                .count()
                .suppress(Suppressed.untilWindowCloses(unbounded()))
                .toStream()
                .to(OUTPUT_TOPIC, producedCount);
```

Let's review this snippet line by line.

``` java
         .selectKey((key, value) -> value.id())
```

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `selectKey` operation specifying that the ID field in the value should be used as the key.

``` java
        .groupByKey(groupedPressure)
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
        TimeWindows windows = TimeWindows
            .ofSizeAndGrace(Duration.ofSeconds(10), Duration.ofSeconds(20))
            .advanceBy(Duration.ofSeconds(10));
```

``` java
        .windowedBy(windows)
```
This creates a windowed record stream with 10 second tumbling windows that allow a 20-second grace period during which late events may arrive.

``` java
        .count()
```

Here we count the number of records per grouped key and window using the `count` convenience method.

``` java
        .suppress(Suppressed.untilWindowCloses(unbounded()))
```

The `count` operator returns a `KTable` on which we can suppress updates until the window closes (after the 20-second grace period).


``` java
        .toStream()
        .to(OUTPUT_TOPIC, producedCount);
```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) and emitted to the output topic.

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
  --environment-name kafka-streams-window-final-result-env \
  --kafka-cluster-name kafka-streams-window-final-result-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./window-final-result/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create final-result-input --partitions 1
confluent kafka topic create final-result-output --partitions 1
```

Start a console producer:

```shell
confluent kafka topic produce final-result-input --parse-key --delimiter :
```

Enter a few JSON-formatted pressure readings:

```plaintext
101:{"id":"101", "dateTime":"2025-09-14T05:30:01.+0200", "pressure":"100"}
101:{"id":"101", "dateTime":"2025-09-14T05:30:02.+0200", "pressure":"100"}
101:{"id":"101", "dateTime":"2025-09-14T05:30:03.+0200", "pressure":"100"}
102:{"id":"102", "dateTime":"2025-09-14T05:30:01.+0200", "pressure":"100"}
102:{"id":"102", "dateTime":"2025-09-14T05:30:02.+0200", "pressure":"100"}
102:{"id":"102", "dateTime":"2025-09-14T05:30:03.+0200", "pressure":"100"}
103:{"id":"103", "dateTime":"2025-09-14T05:30:01.+0200", "pressure":"100"}
103:{"id":"103", "dateTime":"2025-09-14T05:30:02.+0200", "pressure":"100"}
103:{"id":"103", "dateTime":"2025-09-14T05:30:03.+0200", "pressure":"100"}
XXX:{"id":"XXX", "dateTime":"2025-09-14T06:30:03.+0200", "pressure":"100"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew window-final-result:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd window-final-result/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/window-final-result-standalone.jar \
    io.confluent.developer.WindowFinalResult \
    ./src/main/resources/cloud.properties
```

Validate that you see only the final pressure reading count per window (count of 3 for the three sensors).

```shell
confluent kafka topic consume final-result-output -b \
  --value-format integer
```

## Clean up

When you are finished, delete the `kafka-streams-window-final-result-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic final-result-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic final-result-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic final-result-input \
    --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted pressure readings:

  ```plaintext
  101:{"id":"101", "dateTime":"2025-09-14T05:30:01.+0200", "pressure":"100"}
  101:{"id":"101", "dateTime":"2025-09-14T05:30:02.+0200", "pressure":"100"}
  101:{"id":"101", "dateTime":"2025-09-14T05:30:03.+0200", "pressure":"100"}
  102:{"id":"102", "dateTime":"2025-09-14T05:30:01.+0200", "pressure":"100"}
  102:{"id":"102", "dateTime":"2025-09-14T05:30:02.+0200", "pressure":"100"}
  102:{"id":"102", "dateTime":"2025-09-14T05:30:03.+0200", "pressure":"100"}
  103:{"id":"103", "dateTime":"2025-09-14T05:30:01.+0200", "pressure":"100"}
  103:{"id":"103", "dateTime":"2025-09-14T05:30:02.+0200", "pressure":"100"}
  103:{"id":"103", "dateTime":"2025-09-14T05:30:03.+0200", "pressure":"100"}
  XXX:{"id":"XXX", "dateTime":"2025-09-14T06:30:03.+0200", "pressure":"100"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew window-final-result:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd window-final-result/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/window-final-result-standalone.jar \
      io.confluent.developer.WindowFinalResult \
      ./src/main/resources/local.properties
  ```

  Validate that you see only the final pressure reading count per window (count of 3 for the three sensors). In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic final-result-output --from-beginning \
    --property "value.deserializer=org.apache.kafka.common.serialization.IntegerDeserializer"
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
