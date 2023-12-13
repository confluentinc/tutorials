# Kafka Streams Aggregation - Min-Max

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

## Running the example

<details>
  <summary>Running Kafka in Docker</summary>

* [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

### Start Kafka

* Execute ``` confluent local kafka start```  from a terminal window, and copy the `host:port` output
* Save the file `confluent.properties.orig` as `confluent.properties` (ignored by git) and update the `bootstrap.servers` config with the value from the previous step

### Create the topics `min-max-input` and `min-max-output`

* `confluent local kafka topic create min-max-input`
* `confluent local kafka topic create min-max-output`

### Start Kafka Streams

* CD into the `aggregating-minmax/kstreams` directory
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-minmax-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties`.  If you're using something else you'll need to add the path to the command, i.e., `java -jar build/libs/aggregating-minmax-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run the following command to add some records for the Kafka Streams application to process

* `confluent local kafka topic produce min-max-input < src/main/resources/input.txt`


### View the results

Go back to the terminal window running the Kafka Streams application.  You should see something like this
```text
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Avengers: Endgame, releaseYear=2019, totalSales=856980506]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Captain Marvel, releaseYear=2019, totalSales=426829839]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Toy Story 4, releaseYear=2019, totalSales=401486230]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=The Lion King, releaseYear=2019, totalSales=385082142]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Black Panther, releaseYear=2018, totalSales=700059566]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Avengers: Infinity War, releaseYear=2018, totalSales=678815482]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Deadpool 2, releaseYear=2018, totalSales=324512774]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Beauty and the Beast, releaseYear=2017, totalSales=517218368]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Wonder Woman, releaseYear=2017, totalSales=412563408]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Star Wars Ep. VIII: The Last Jedi, releaseYear=2017, totalSales=517218368]]

```
Note that incoming records don't have keys, the `KStream.groupByKey` function sets the keys to enable the aggregation.
Then roughly in 30 seconds you should see the average output like this:

```text
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Aggregation min-max results key[2017] value[YearlyMovieFigures[releaseYear=2017, minTotalSales=412563408, maxTotalSales=517218368]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Aggregation min-max results key[2018] value[YearlyMovieFigures[releaseYear=2018, minTotalSales=324512774, maxTotalSales=700059566]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Aggregation min-max results key[2019] value[YearlyMovieFigures[releaseYear=2019, minTotalSales=385082142, maxTotalSales=856980506]]
```


</details>

<details>
  <summary>Confluent Cloud</summary>

### Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)

<details>
     <summary>Creating a cluster in the Cloud UI</summary>

Create Kafka cluster following [these directions](https://docs.confluent.io/cloud/current/get-started/index.html)

### Get the configuration

Once the Kafka cluster is up and running all you need to next is get the client configuration.

* In the Confluent Cloud Console, click on the `Clients` option in the left-hand menu.
* Click on the `Java` tile and create the cluster API key and a Schema Registry API key
* Copy the generated properties into the `confluent.properties.orig` file and save it as `confluent.properties` (ignored by git)
</details>

<details>
  <summary>Creating a cluster with the CLI</summary>

If you already have a Confluent Cloud account, and you don't yet have a Kafka cluster and credentials for connecting to it, you can get started with CLI exclusively.

* Run the CLI command  `confluent plugin install confluent-cloud_kickstart`
* Then execute `confluent cloud-kickstart --name <CLUSTER NAME>` which will create a cluster, enable Schema Registry, and create all required API keys.  This will create a cluster with default settings. To see all the options available use `confluent cloud-kickstart --help`.
* Copy the generated client configuration (located in `~/Downloads/java_configs_<CLUSTER_ID>` by default) into `confluent.properties.org` and save as `confluent.properties`. The full location of the properties file is printed to the console.

</details>

### Create the topics `min-max-input` and `min-max-output`

*_Note that if you create the cluster using the CLI plugin you can omit the `--cluster` argument from the commands_*

* `confluent kafka topic create min-max-input --cluster <CLUSTER_ID>`
* `confluent kafka topic create min-max-output --cluster <CLUSTER_ID>`

### Start Kafka Streams

* CD into the `aggregating-minmax/kstreams` directory
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-minmax-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/aggregating-minmax-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run the following command to add some records for the Kafka Streams application to process

* `confluent kafka topic produce min-max-input --cluster <CLUSTER_ID>  < src/main/resources/input.txt`


### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this:
```text
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Avengers: Endgame, releaseYear=2019, totalSales=856980506]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Captain Marvel, releaseYear=2019, totalSales=426829839]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Toy Story 4, releaseYear=2019, totalSales=401486230]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=The Lion King, releaseYear=2019, totalSales=385082142]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Black Panther, releaseYear=2018, totalSales=700059566]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Avengers: Infinity War, releaseYear=2018, totalSales=678815482]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Deadpool 2, releaseYear=2018, totalSales=324512774]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Beauty and the Beast, releaseYear=2017, totalSales=517218368]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Wonder Woman, releaseYear=2017, totalSales=412563408]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Incoming data key[] value[MovieTicketSales[title=Star Wars Ep. VIII: The Last Jedi, releaseYear=2017, totalSales=517218368]]
```
Note that, because the incoming records don't have keys, the `KStream.groupBy` function sets the keys to enable the aggregation.
Then roughly in 30 seconds you should see the average output like this:

```text
 [aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Aggregation min-max results key[2017] value[YearlyMovieFigures[releaseYear=2017, minTotalSales=412563408, maxTotalSales=517218368]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Aggregation min-max results key[2018] value[YearlyMovieFigures[releaseYear=2018, minTotalSales=324512774, maxTotalSales=700059566]]
[aggregating-min-max-916da607-de0e-4b22-a9fc-5047470cf666-StreamThread-1] INFO io.confluent.developer.AggregatingMinMax - Aggregation min-max results key[2019] value[YearlyMovieFigures[releaseYear=2019, minTotalSales=385082142, maxTotalSales=856980506]]
```
</details>


