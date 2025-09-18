<!-- title: How to aggregate over tumbling windows with Kafka Streams -->
<!-- description: In this tutorial, learn how to aggregate over tumbling windows with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to aggregate over tumbling windows with Kafka Streams

If you have time series events in a Kafka topic, tumbling windows let you group and aggregate them in fixed-size, non-overlapping, contiguous time intervals.

For example, you have a topic with events that represent movie ratings. The following topology definition counts the ratings per title over 10-minute tumbling windows.

``` java
  builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieRatingSerde))
    .map((key, rating) -> new KeyValue<>(rating.title(), rating))
    .groupByKey(Grouped.with(Serdes.String(), movieRatingSerde))
    .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(10), Duration.ofMinutes(1440)))
    .count()
    .toStream()
    .map((Windowed<String> key, Long count) -> new KeyValue<>(key.key(), count))
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()));
```

Let's review the key points in this example

``` java
    .map((key, rating) -> new KeyValue<>(rating.title(), rating))
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `map` operation which creates new key-value pairs setting the key of the stream to the `MovieRating.title` field.

``` java
    .groupByKey(Grouped.with(Serdes.String(), movieRatingSerde))
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
    .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(10), Duration.ofMinutes(1440)))
```

This creates a new `TimeWindowedKStream` that we can aggregate. The tumbling windows are 10 minutes long, and we allow data to arrive late by as much as a day.

``` java
    .count()
```

The `count()` operator is a convenience aggregation method.  Under the covers it works like any other aggregation in Kafka Streams i.e. it requires an
`Initializer`, [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) and a [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) to set the `Serde` for the value since it's a `long`.  But since the result of this aggregation is a simple count, Kafka Streams handles all those details for you.

``` java
    .toStream()
    .map((Windowed<String> key, Long count) -> new KeyValue<>(key.key(), count))
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Long()));
```

Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).
Then [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) converts to the expected data types.

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
  --environment-name kafka-streams-tumbling-windows-env \
  --kafka-cluster-name kafka-streams-tumbling-windows-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./tumbling-windows/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create ratings
confluent kafka topic create rating-counts
```

Start a console producer:

```shell
confluent kafka topic produce ratings --parse-key --delimiter :
```

Enter a few JSON-formatted movie ratings:

```plaintext
Super Mario Bros.:{"title":"Super Mario Bros.", "release_year":1993, "rating":3.5, "timestamp":"2024-09-25T11:15:00-0000"}
Super Mario Bros.:{"title":"Super Mario Bros.", "release_year":1993, "rating":2.0, "timestamp":"2024-09-25T11:40:00-0000"}
A Walk in the Clouds:{"title":"A Walk in the Clouds", "release_year":1998, "rating":3.6, "timestamp":"2024-09-25T13:00:00-0000"}
A Walk in the Clouds:{"title":"A Walk in the Clouds", "release_year":1998, "rating":7.1, "timestamp":"2024-09-25T13:01:00-0000"}
Die Hard:{"title":"Die Hard", "release_year":1988, "rating":8.2, "timestamp":"2024-09-25T18:00:00-0000"}
Die Hard:{"title":"Die Hard", "release_year":1988, "rating":7.6, "timestamp":"2024-09-25T18:05:00-0000"}
The Big Lebowski:{"title":"The Big Lebowski", "release_year":1998, "rating":8.6, "timestamp":"2024-09-25T19:30:00-0000"}
The Big Lebowski:{"title":"The Big Lebowski", "release_year":1998, "rating":7.0, "timestamp":"2024-09-25T19:35:00-0000"}
Tree of Life:{"title":"Tree of Life", "release_year":2011, "rating":4.9, "timestamp":"2024-09-25T21:00:00-0000"}
Tree of Life:{"title":"Tree of Life", "release_year":2011, "rating":9.9, "timestamp":"2024-09-25T21:11:00-0000"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew tumbling-windows:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd tumbling-windows/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/tumbling-windows-standalone.jar \
    io.confluent.developer.TumblingWindow \
    ./src/main/resources/cloud.properties
```

Validate that you see correct rating counts in the `rating-counts` topic.

```shell
confluent kafka topic consume rating-counts -b \
  --print-key --delimiter : --value-format integer
```

You should see:

```shell
Super Mario Bros.:1
Super Mario Bros.:1
A Walk in the Clouds:1
A Walk in the Clouds:2
Die Hard:1
Die Hard:2
The Big Lebowski:1
The Big Lebowski:2
Tree of Life:1
Tree of Life:1
```

## Clean up

When you are finished, delete the `kafka-streams-tumbling-windows-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic ratings
  kafka-topics --bootstrap-server localhost:9092 --create --topic rating-counts
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic ratings \
    --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted movie ratings:

  ```plaintext
  Super Mario Bros.:{"title":"Super Mario Bros.", "release_year":1993, "rating":3.5, "timestamp":"2024-09-25T11:15:00-0000"}
  Super Mario Bros.:{"title":"Super Mario Bros.", "release_year":1993, "rating":2.0, "timestamp":"2024-09-25T11:40:00-0000"}
  A Walk in the Clouds:{"title":"A Walk in the Clouds", "release_year":1998, "rating":3.6, "timestamp":"2024-09-25T13:00:00-0000"}
  A Walk in the Clouds:{"title":"A Walk in the Clouds", "release_year":1998, "rating":7.1, "timestamp":"2024-09-25T13:01:00-0000"}
  Die Hard:{"title":"Die Hard", "release_year":1988, "rating":8.2, "timestamp":"2024-09-25T18:00:00-0000"}
  Die Hard:{"title":"Die Hard", "release_year":1988, "rating":7.6, "timestamp":"2024-09-25T18:05:00-0000"}
  The Big Lebowski:{"title":"The Big Lebowski", "release_year":1998, "rating":8.6, "timestamp":"2024-09-25T19:30:00-0000"}
  The Big Lebowski:{"title":"The Big Lebowski", "release_year":1998, "rating":7.0, "timestamp":"2024-09-25T19:35:00-0000"}
  Tree of Life:{"title":"Tree of Life", "release_year":2011, "rating":4.9, "timestamp":"2024-09-25T21:00:00-0000"}
  Tree of Life:{"title":"Tree of Life", "release_year":2011, "rating":9.9, "timestamp":"2024-09-25T21:11:00-0000"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew tumbling-windows:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd tumbling-windows/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/tumbling-windows-standalone.jar \
      io.confluent.developer.TumblingWindow \
      ./src/main/resources/local.properties
  ```

  Validate that you see correct rating counts in the `rating-counts` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic rating-counts --from-beginning \
    --property "print.key=true" --property "key.separator=:" \
    --property "value.deserializer=org.apache.kafka.common.serialization.IntegerDeserializer"
  ```

  You should see rating counts per movie for ten-minute windows:

  ```shell
  Super Mario Bros.:1
  Super Mario Bros.:1
  A Walk in the Clouds:1
  A Walk in the Clouds:2
  Die Hard:1
  Die Hard:2
  The Big Lebowski:1
  The Big Lebowski:2
  Tree of Life:1
  Tree of Life:1
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
