# Kafka Streams Aggregation-Sum

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


## Running the example

<details>
  <summary>Running Kafka in Docker</summary>

* [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

### Start Kafka

* Execute ```confluent local kafka start```  from a terminal window, and copy the `host:port` output
* Save the file `confluent.properties.orig` as `confluent.properties` (ignored by git) and update the `bootstrap.servers` config with the value from the previous step

### Create the topics `aggregation-sum-input` and `aggregation-sum-output`

* `confluent local kafka topic create aggregation-sum-input`
* `confluent local kafka topic create aggregation-sum-output`

### Start Kafka Streams

* CD into the `aggregating-sum/kstreams` directory
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-sum-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/aggregating-sum-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run the following command to add some records for the Kafka Streams application to process.

* `confluent local kafka topic produce aggregation-sum-input < src/main/resources/input.txt`


### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this:
```text
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
```
Note that, because incoming records don't have keys, the `KStream.map` function sets the keys to enable the aggregation.
Then roughly in 30 seconds you should see the average output like this:

```text
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Reduce result key[The Godfather] value[60 total sales]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Reduce result key[Guardians of the Galaxy] value[60 total sales]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Reduce result key[Doctor Strange] value[60 total sales]
```


</details>

<details>
  <summary>Confluent Cloud</summary>

### Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)

<details>
     <summary>Creating a cluster in the Confluent Cloud Console</summary>

Create Kafka cluster following [these directions](https://docs.confluent.io/cloud/current/get-started/index.html)

### Get the configuration

Once Kafka is up and running all you need to next is get the client configuration.

* In the Confluent Cloud Console, click on the `Clients` option in the left-hand menu.
* Click on the `Java` tile and create the cluster API key and a Schema Registry API key
* Copy the generated properties into the `confluent.properties.orig` file and save it as `confluent.properties` (ignored by git)
</details>

<details>
  <summary>Creating a cluster with the CLI</summary>

If you already have a Confluent Cloud account, and you don't yet have a Kafka cluster and credentials for connecting to it, you can get started with CLI exclusively.

* Run the CLI command  `confluent plugin install confluent-cloud_kickstart`
* Then execute `confluent cloud-kickstart --name <CLUSTER NAME>` which will create a cluster, enable Schema Registry, and create all required API keys.  This will create a cluster with default settings. To see all the options available use `confluent cloud-kickstart --help`.
* Copy the generated client configurations (located in `~/Downloads/java_configs_<CLUSTER_ID>` by default) into `confluent.properties.org` and save as `confluent.properties`. The full location of the properties file is printed to the console.

</details>

### Create the topics `aggregation-sum-input` and `aggregation-sum-output`

*_Note that if you create the cluster using the CLI plugin you can omit the `--cluster` argument from the commands_*

* `confluent kafka topic create aggregation-sum-input --cluster <CLUSTER_ID>`
* `confluent kafka topic create aggregation-sum-output --cluster <CLUSTER_ID>`

### Start Kafka Streams

* CD into the `aggregating-sum/kstreams` directory
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-sum-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/aggregating-sum-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run the following commands to add some records for the Kafka Streams application to process.

* `confluent kafka topic produce aggregation-sum-input --cluster <CLUSTER_ID>  < src/main/resources/input.txt`


### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this:
```text
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Guardians of the Galaxy, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=Doctor Strange, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Incoming record key[] value[TicketSale[title=The Godfather, ticketTotalValue=15]]
```
Note that, because the incoming records don't have keys, the `KStream.map` function sets the keys to enable the aggregation.
Then roughly in 30 seconds you should see the average output like this:

```text
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Reduce result key[The Godfather] value[60 total sales]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Reduce result key[Guardians of the Galaxy] value[60 total sales]
[aggregating-sum-250767e8-e82b-4b4f-b10e-d4cabc22ab7b-StreamThread-1] INFO io.confluent.developer.AggregatingSum - Reduce result key[Doctor Strange] value[60 total sales]
```
</details>