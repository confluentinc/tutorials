<!-- title: How to aggregate over sliding windows with Kafka Streams -->
<!-- description: In this tutorial, learn how to aggregate over sliding windows with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to aggregate over sliding windows with Kafka Streams

If you have time series events in a Kafka topic, sliding windows let you group and aggregate them in _small_ fixed-size, contiguous time intervals. Semantically,
this is the same idea as hopping windows; however, for performance reasons, hopping windows aren't the best solution for small time increments.

For example, you have a topic with events that represent temperature readings from a sensor. The following topology definition computes the average temperature for a given sensor over small 0.5-second sliding windows.

``` java
    builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), tempReadingSerde))
            .groupByKey()
            .windowedBy(SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofMillis(500), Duration.ofMillis(100)))
            .aggregate(() -> new TempAverage(0, 0),
                    (key, value, agg) -> new TempAverage(agg.total() + value.temp(), agg.num_readings() + 1),
                    Materialized.with(Serdes.String(), tempAverageSerde))
            .toStream()
            .map((Windowed<String> key, TempAverage tempAverage) -> {
                double aveNoFormat = tempAverage.total()/(double)tempAverage.num_readings();
                double formattedAve = Double.parseDouble(String.format("%.2f", aveNoFormat));
                return new KeyValue<>(key.key(),formattedAve) ;
            })
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));
```

Let's review the key points in this example

``` java
    .groupByKey()
```  

Aggregations must group records by key so grouping by key is the first step in the topology.

``` java
    .windowedBy(SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofMillis(500), Duration.ofMillis(100)))
```

This creates a new `TimeWindowedKStream` that we can aggregate. The sliding windows are 500 ms long, and we allow data to arrive late by as much as 100 ms.

``` java
    .aggregate(() -> new TempAverage(0, 0),
                    (key, value, agg) -> new TempAverage(agg.total() + value.temp(), agg.num_readings() + 1),
                    Materialized.with(Serdes.String(), tempAverageSerde))
```

Here we update the sum of temperature readings and the number of readings processed. These values are used to calculate the average temperature downstream in the topology.

``` java
    .toStream()
    .map((Windowed<String> key, TempAverage tempAverage) -> {
        double aveNoFormat = tempAverage.total()/(double)tempAverage.num_readings();
        double formattedAve = Double.parseDouble(String.format("%.2f", aveNoFormat));
        return new KeyValue<>(key.key(),formattedAve) ;
    })
    .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));
```

Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).
Then [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) is used to calculate the average temperature before we finally emit the aggregate to the output topic.

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
  --environment-name kafka-streams-sliding-windows-env \
  --kafka-cluster-name kafka-streams-sliding-windows-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./sliding-windows/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create temp-readings
confluent kafka topic create output-topic
```

Start a console producer:

```shell
confluent kafka topic produce temp-readings --parse-key --delimiter :
```

Enter a few JSON-formatted temperature readings:

```plaintext
device-1:{"temp":80.0,"timestamp":1757703142,"device_id":"device-1"}
device-1:{"temp":90.0,"timestamp":1757703142,"device_id":"device-1"}
device-1:{"temp":95.0,"timestamp":1757703142,"device_id":"device-1"}
device-1:{"temp":100.0,"timestamp":1757703142,"device_id":"device-1"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew sliding-windows:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd sliding-windows/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/sliding-windows-standalone.jar \
    io.confluent.developer.SlidingWindow \
    ./src/main/resources/cloud.properties
```

Validate that you see the correct temperature averages in the `output-topic` topic.

```shell
confluent kafka topic consume output-topic -b \
  --print-key --delimiter : --value-format double
```

You should see the average updated within the same sliding window:

```shell
device-1:80.0
device-1:85.0
device-1:88.33
device-1:91.25
```

## Clean up

When you are finished, delete the `kafka-streams-sliding-windows-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic temp-readings
  kafka-topics --bootstrap-server localhost:9092 --create --topic output-topic
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic temp-readings \
    --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted temperature readings:

  ```plaintext
  device-1:{"temp":80.0,"timestamp":1757703142,"device_id":"device-1"}
  device-1:{"temp":90.0,"timestamp":1757703143,"device_id":"device-1"}
  device-1:{"temp":95.0,"timestamp":1757703144,"device_id":"device-1"}
  device-1:{"temp":100.0,"timestamp":1757703145,"device_id":"device-1"}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew sliding-windows:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd sliding-windows/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/sliding-windows-standalone.jar \
      io.confluent.developer.SlidingWindow \
      ./src/main/resources/local.properties
  ```

  Validate that you see the correct temperature averages in the `output-topic` topic.

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic output-topic --from-beginning \
    --property "print.key=true" --property "key.separator=:" \
    --property "value.deserializer=org.apache.kafka.common.serialization.DoubleDeserializer"
  ```

  You should see the average updated within the same sliding window:

  ```shell
  device-1:80.0
  device-1:85.0
  device-1:88.33
  device-1:91.25
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
