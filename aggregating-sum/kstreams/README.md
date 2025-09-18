<!-- title: How to compute the sum of a field with Kafka Streams -->
<!-- description: In this tutorial, learn how to compute the sum of a field with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to compute the sum of a field with Kafka Streams

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In our example here
we're going to use the `reduce` method to sum the total amount of tickets sold by title.

``` java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), ticketSaleSerde))
        .map((k, v) -> KeyValue.pair(v.title(), v.ticketTotalValue()))
        .groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
        .reduce(Integer::sum)
        .toStream()
        .mapValues(v -> String.format("%d total sales",v))
        .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
```

Let's review the key points in this example

``` java
    map((key, value) -> new KeyValue<>(v.title(), v.ticketTotalValue()))
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `map` operation which creates new key-value pairs setting the key of the stream to the `TicketSale.title` field.

``` java
        groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
 .reduce(Integer::sum)
```

The [Reduce](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#reduce-org.apache.kafka.streams.kstream.Reducer-) operator is a special type of aggregation. A `reduce` returns the same type as the original input, in this case a sum of the current value with the previously computed value. The `reduce` method takes an instance of a [Reducer](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Reducer.html). Since a `Reducer` is a single method interface you can use method handle instead of a concrete object. In this case it's [Integer.sum](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#sum(int,int)) method that takes two integers and adds them together.

``` java
                .toStream()
                .mapValues(v -> String.format("%d total sales",v))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html)instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) then [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) appends a string to the count to give it some context on the meaning of the number.

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
  --environment-name kafka-streams-aggregating-sum-env \
  --kafka-cluster-name kafka-streams-aggregating-sum-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./aggregating-sum/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create aggregation-sum-input
confluent kafka topic create aggregation-sum-output
```

Start a console producer:

```shell
confluent kafka topic produce aggregation-sum-input
```

Enter a few JSON-formatted ticket sales:

```plaintext
{"title":"Guardians of the Galaxy", "ticketTotalValue":15}
{"title":"Doctor Strange", "ticketTotalValue":15}
{"title":"Guardians of the Galaxy", "ticketTotalValue":15}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew aggregating-sum:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd aggregating-sum/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/aggregating-sum-standalone.jar \
    io.confluent.developer.AggregatingSum \
    ./src/main/resources/cloud.properties
```

Validate that you see the correct total ticket sales per title in the `aggregation-sum-output` topic.

```shell
confluent kafka topic consume aggregation-sum-output -b --print-key --delimiter :
```

You should see:

```shell
Doctor Strange:15 total sales
Guardians of the Galaxy:30 total sales
```

## Clean up

When you are finished, delete the `kafka-streams-aggregating-sum-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic aggregation-sum-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic aggregation-sum-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic aggregation-sum-input
  ```

  Enter a few JSON-formatted ticket sales:

  ```plaintext
  {"title":"Guardians of the Galaxy", "ticketTotalValue":15}
  {"title":"Doctor Strange", "ticketTotalValue":15}
  {"title":"Guardians of the Galaxy", "ticketTotalValue":15}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew aggregating-sum:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd aggregating-sum/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/aggregating-sum-standalone.jar \
      io.confluent.developer.AggregatingSum \
      ./src/main/resources/local.properties
  ```

  Validate that you see the correct total ticket sales per title in the `aggregation-sum-output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic aggregation-sum-output --from-beginning --property "print.key=true" --property "key.separator=:"
  ```

  You should see:

  ```shell
  Doctor Strange:15 total sales
  Guardians of the Galaxy:30 total sales
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
