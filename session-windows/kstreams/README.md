<!-- title: How to aggregate over session windows with Kafka Streams -->
<!-- description: In this tutorial, learn how to aggregate over session windows with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to aggregate over session windows with Kafka Streams

If you have time series events in a Kafka topic, session windows let you group and aggregate them into variable-size, non-overlapping time intervals based on a configurable inactivity period.

For example, suppose that you have a topic with events that represent website clicks. The following topology definition counts the number of clicks per source IP address for windows that close after 5 minutes of inactivity.
``` java
    builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), clickSerde))
            .groupByKey()
            .windowedBy(SessionWindows.ofInactivityGapAndGrace(Duration.ofMinutes(5), Duration.ofSeconds(30)))
            .count()
            .toStream()
            .map((windowedKey, count) ->  {
                String start = timeFormatter.format(windowedKey.window().startTime());
                String end = timeFormatter.format(windowedKey.window().endTime());
                String sessionInfo = String.format("Session info started: %s ended: %s with count %s", start, end, count);
                return KeyValue.pair(windowedKey.key(), sessionInfo);
            })
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
```

Let's review the key points in this example.

``` java
    .groupByKey()
```

Aggregations must group records by key. By not passing an argument, we use the current key (the source IP address).

``` java
    .windowedBy(SessionWindows.ofInactivityGapAndGrace(Duration.ofMinutes(5), Duration.ofSeconds(30)))
```

This creates a new `SessionWindowedKStream` over which we can aggregate. The session windows close after 5 minutes of inactivity, and we allow data to arrive late by as much as 30 seconds.

``` java
    .count()
```

The `count()` operator is a convenience aggregation method.  Under the covers it works like any other aggregation in Kafka Streams — i.e., it requires an
`Initializer`, [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) and a [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) to set the `Serde` for the value since it's a `long`.  But, since the result of this aggregation is a simple count, Kafka Streams handles those details for you.

``` java
    .toStream()
    .map((windowedKey, count) ->  {
        String start = timeFormatter.format(windowedKey.window().startTime());
        String end = timeFormatter.format(windowedKey.window().endTime());
        String sessionInfo = String.format("Session info started: %s ended: %s with count %s", start, end, count);
        return KeyValue.pair(windowedKey.key(), sessionInfo);
    })
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
```

Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).
Then [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) converts to the expected data types. The value is a formatted String containing session information.

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
  --environment-name kafka-streams-session-windows-env \
  --kafka-cluster-name kafka-streams-session-windows-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./session-windows/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create clicks
confluent kafka topic create sessions
```

Start a console producer:

```shell
confluent kafka topic produce clicks --parse-key --delimiter :
```

Enter a few JSON-formatted clicks:

```plaintext
182.12.112.1:{"ip":"182.12.112.1", "timestamp":1757966638000, "url":"https://shd.com/index.html"}
182.12.112.1:{"ip":"182.12.112.1", "timestamp":1757967148000, "url":"https://shd.com/about.html"}
182.12.112.1:{"ip":"182.12.112.1", "timestamp":1757967648000, "url":"https://shd.com/index.html"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew session-windows:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd session-windows/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/session-windows-standalone.jar \
    io.confluent.developer.SessionWindow \
    ./src/main/resources/cloud.properties
```

Validate that you see three separate sessions in the `sessions` topic.

```shell
confluent kafka topic consume sessions -b \
  --print-key --delimiter : --value-format integer
```

You should see:

```shell
182.12.112.1:Session info started: 4:03:58 PM EDT ended: 4:03:58 PM EDT with count 1
182.12.112.1:Session info started: 4:12:28 PM EDT ended: 4:12:28 PM EDT with count 1
182.12.112.1:Session info started: 4:20:48 PM EDT ended: 4:20:48 PM EDT with count 1
```

## Clean up

When you are finished, delete the `kafka-streams-session-windows-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic clicks
  kafka-topics --bootstrap-server localhost:9092 --create --topic sessions
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic clicks \
    --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted clicks:

  ```plaintext
  182.12.112.1:{"ip":"182.12.112.1", "timestamp":1757966638000, "url":"https://shd.com/index.html"}
  182.12.112.1:{"ip":"182.12.112.1", "timestamp":1757967148000, "url":"https://shd.com/about.html"}
  182.12.112.1:{"ip":"182.12.112.1", "timestamp":1757967648000, "url":"https://shd.com/index.html"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew session-windows:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd session-windows/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/session-windows-standalone.jar \
      io.confluent.developer.SessionWindow \
      ./src/main/resources/local.properties
  ```

  Validate that you see three separate sessions in the `sessions` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic sessions --from-beginning \
    --property "print.key=true" --property "key.separator=:"
  ```

  You should see:

  ```shell
  182.12.112.1:Session info started: 4:03:58 PM EDT ended: 4:03:58 PM EDT with count 1
  182.12.112.1:Session info started: 4:12:28 PM EDT ended: 4:12:28 PM EDT with count 1
  182.12.112.1:Session info started: 4:20:48 PM EDT ended: 4:20:48 PM EDT with count 1
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
