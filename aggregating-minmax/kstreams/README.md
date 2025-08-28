<!-- title: How to compute the minimum or maximum value of a field with Kafka Streams -->
<!-- description: In this tutorial, learn how to compute the minimum or maximum value of a field with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to compute the minimum or maximum value of a field with Kafka Streams

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In this example
the input value is a `MovieTicketSales` object but the result is a `YearlyMovieFigures` object used to keep track of the minimum and maximum total
ticket sales by release year. You can also use windowing with aggregations to get discrete results per segment of time.


``` java
       builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieSalesSerde))
              .groupBy((k, v) -> v.releaseYear(),
                      Grouped.with(Serdes.Integer(), movieSalesSerde))
              .aggregate(() -> new YearlyMovieFigures(0, Integer.MAX_VALUE, Integer.MIN_VALUE),
                      ((key, value, aggregate) ->
                              new YearlyMovieFigures(key,
                                      Math.min(value.totalSales(), aggregate.minTotalSales()),
                                      Math.max(value.totalSales(), aggregate.maxTotalSales()))),
                      Materialized.with(Serdes.Integer(), yearlySalesSerde))
              .toStream()
              .peek((key, value) -> LOG.info("Aggregation min-max results key[{}] value[{}]", key, value))
              .to(OUTPUT_TOPIC, Produced.with(Serdes.Integer(), yearlySalesSerde));
```

Let's review the key points in this example

``` java
   .groupBy((k, v) -> v.releaseYear(),
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `groupByKey` operation on the `releaseYear` field
of the `MovieTicketSales` value object.

``` java
        .groupBy((k, v) -> v.releaseYear(), Grouped.with(Serdes.Integer(), movieSalesSerde) 
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
.aggregate(() -> new YearlyMovieFigures(0, Integer.MAX_VALUE, Integer.MIN_VALUE),
                      ((key, value, aggregate) ->
                              new YearlyMovieFigures(key,
                                      Math.min(value.totalSales(), aggregate.minTotalSales()),
                                      Math.max(value.totalSales(), aggregate.maxTotalSales()))),
                      Materialized.with(Serdes.Integer(), yearlySalesSerde))
```

This aggregation performs a running average of movie ratings.  To enable this, it keeps the running sum and count of the ratings.  The [aggregate](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Materialized-) operator takes 3 parameters (there are overloads that accept [2](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-) and [4](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Named-org.apache.kafka.streams.kstream.Materialized-) parameters):

1. An initializer for the default value in this case a new instance of the `YearlyMovieFigures` object which is a Java POJO containing current min and max sales.
2. An [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) instance which performs the aggregation action.  Here the code uses a Java [lambda expression](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html) instead of a concrete object instance.
3. A [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) object describing how the underlying [StateStore](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/processor/StateStore.html) is materialized.


``` java
 .toStream()
 .to(OUTPUT_TOPIC, Produced.with(Serdes.Integer(), yearlySalesSerde));
```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html).  Then results are produced to an output topic via the `to` DSL operator.

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
  --environment-name kafka-streams-aggregating-minmax-env \
  --kafka-cluster-name kafka-streams-aggregating-minmax-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./aggregating-minmax/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create min-max-input
confluent kafka topic create min-max-output
```

Start a console producer:

```shell
confluent kafka topic produce min-max-input
```

Enter a few JSON-formatted movie sales:

```plaintext
{"title":"Guardians of the Galaxy", "releaseYear":2020, "totalSales":300000000}
{"title":"Spiderman", "releaseYear":2020, "totalSales":200000000}
{"title":"The Poseidon Adventure", "releaseYear":1972, "totalSales":42000000}
{"title":"Cabaret", "releaseYear":1972, "totalSales":22000000}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew aggregating-minmax:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd aggregating-minmax/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/aggregating-minmax-standalone.jar \
    io.confluent.developer.AggregatingMinMax \
    ./src/main/resources/cloud.properties
```

Validate that you see the correct min and max ticket sales per year in the `min-max-output` topic.

```shell
confluent kafka topic consume min-max-output -b
```

You should see:

```shell
{"releaseYear":2020,"minTotalSales":200000000,"maxTotalSales":300000000}
{"releaseYear":1972,"minTotalSales":22000000,"maxTotalSales":42000000}
```

## Clean up

When you are finished, delete the `kafka-streams-aggregating-minmax-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic min-max-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic min-max-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic min-max-input
  ```

  Enter a few JSON-formatted movie sales:

  ```plaintext
  {"title":"Guardians of the Galaxy", "releaseYear":2020, "totalSales":300000000}
  {"title":"Spiderman", "releaseYear":2020, "totalSales":200000000}
  {"title":"The Poseidon Adventure", "releaseYear":1972, "totalSales":42000000}
  {"title":"Cabaret", "releaseYear":1972, "totalSales":22000000}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew aggregating-minmax:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd aggregating-minmax/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/aggregating-minmax-standalone.jar \
      io.confluent.developer.AggregatingMinMax \
      ./src/main/resources/local.properties
  ```

  Validate that you see the correct min and max ticket sales per year in the `min-max-output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic min-max-output --from-beginning
  ```

  You should see:

  ```shell
  {"releaseYear":2020,"minTotalSales":200000000,"maxTotalSales":300000000}
  {"releaseYear":1972,"minTotalSales":22000000,"maxTotalSales":42000000}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
