# Kafka Streams Aggregation-Count

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In our example here
we're going to use the `count()` method to perform a count on the number of tickets sold.

``` java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), ticketSaleSerde))
        .map((k, v) -> new KeyValue<>(v.title(), v.ticketTotalValue()))
        .groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
        .count()
        .toStream().mapValues(v -> v.toString() + " tickets sold")
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
count()
```

The `count()` operator is a convenience aggregation method.  Under the covers it works like any other aggregation in Kafka Streams i.e. it requires an
`Initializer`, [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) and a [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) to set the `Serde` for the value since it's a `long`.  But since the result of this aggregation is a simple count, Kafka Streams handles all those details for you.

``` java
                .toStream().mapValues(v -> v + " tickets sold")
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) then [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) appends a string to the count to give it some context on the meaning of the number.


## Running the example

<details>
  <summary>Running Kafka in Docker</summary>

* [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

### Start Kafka

* Execute ```bash confluent local kafka start```  from a terminal window, and copy the `host:port` output
* Save the file `confluent.properties.orig` as `confluent.properties` (ignored by git) and update the `bootstrap.servers` config with the value from the previous step

### Create the topics `aggregation-count-input` and `aggregation-count-output`

* `confluent local kafka topic create aggregation-count-input`
* `confluent local kafka topic create aggregation-count-output`

### Start Kafka Streams

* CD into the `aggregating-count/kstreams` directory 
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-count-standalone.jar` 
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/aggregating-count-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run one of the following commands to add some records for the Kafka Streams application to process

* `confluent local kafka topic produce aggregation-count-input < src/main/resources/input.txt`


### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this
```text
kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
```
Note that incoming records don't have keys, the `KStream.map` function sets the keys to enable the aggregation
Then roughly in 30 seconds you should see the average output like this:

```text
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Count results key[Guardians of the Galaxy] value[4 tickets sold]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Count results key[The Godfather] value[4 tickets sold]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Count results key[Doctor Strange] value[4 tickets sold]
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

After creating a cluster Kafka is up and running all you need to next is get the client configurations.

* In the cloud UI, click on the `Clients` option in the left-hand menu.
* Click on the `Java` tile and create the cluster API key and a Schema Registry API key
* Copy the generated properties into the `confluent.properties.orig` file and save it as `confluent.properties` (ignored by git)
</details>

<details>
  <summary>Creating a cluster with the CLI</summary>

If you already have a cloud account, and you don't yet have a Kafka cluster and credentials for connecting to it, you can get started with CLI exclusively.

* Run the CLI command  `confluent plugin install confluent-cloud_kickstart`
* Then execute `confluent cloud-kickstart --name <CLUSTER NAME>` which will create a cluster, enable Schema Registry and all required API keys.  This will create a cluster with default settings, to see all the options available use `confluent cloud-kickstart --help`
* Copy the generated client configurations (located in `~/Downloads/java_configs_<CLUSTER_ID>` by default) into `confluent.properties.org` and save as `confluent.properties`. The full location of the properties file is printed to the console.

</details>

### Create the topics `aggregation-count-input` and `aggregation-count-output`

*_Note that if you create the cluster using the CLI plugin you can omit the cluster-id from the commands_*

* `confluent kafka topic create aggregation-count-input --cluster <CLUSTER_ID>`
* `confluent kafka topic create aggregation-count-output --cluster <CLUSTER_ID>`

### Start Kafka Streams

* CD into the `aggregating-count/kstreams` directory
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-count-standalone.jar` 
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/aggregating-count-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run the following commands to add some records for the Kafka Streams application to process

* `confluent kafka topic produce aggregation-count-input --cluster <CLUSTER_ID>  < src/main/resources/input.txt`


### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this
```text
kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Incoming records key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
```
Note that incoming records don't have keys, the `KStream.map` function sets the keys to enable the aggregation
Then roughly in 30 seconds you should see the average output like this:

```text
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Count results key[Guardians of the Galaxy] value[4 tickets sold]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Count results key[The Godfather] value[4 tickets sold]
[kafka-streams-aggregating-count-41e61fd2-4e26-4b11-88b3-5ff7d8590fe1-StreamThread-1] INFO io.confluent.developer.KafkaStreamsAggregatingCount - Count results key[Doctor Strange] value[4 tickets sold]
```
</details>

