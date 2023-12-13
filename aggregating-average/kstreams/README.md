# Kafka Streams Aggregation

An aggregation in Kafka Streams is a stateful operation used to perform a "clustering" or "grouping" of values with
the same key.  An aggregation in Kafka Streams may return a different type than the input value.  In our example here
the input value is a `double` but the result is a `CountAndSum` object used later to determine a running average.
You can also use windowing with aggregations to get discrete results per segments of time.

``` java annotate
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieRatingSerde))
                .map((key, value) -> KeyValue.pair(value.id(), value.rating()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                .aggregate(() -> new CountAndSum(0L, 0.0),
                        (key, value, aggregate) -> {
                            double sum = value + aggregate.sum();
                            long count = aggregate.count() + 1;
                            return new CountAndSum(count, sum);
                        },
                        Materialized.with(Serdes.String(), countAndSumSerde))
                .toStream()
                .mapValues(value -> value.sum() / value.count())
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));
```

Let's review the key points in this example

``` java
         map((key, value) -> KeyValue.pair(value.id(), value.rating())
```  

Aggregations must group records by key.  Since the stream source topic doesn't define any, the code has a `map` operation which creates new key-value pairs setting the key of the stream to the `MovieRating.id` field.

``` java
        groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
```

Since you've changed the key, under the covers Kafka Streams performs a repartition immediately before it performs the grouping.  
Repartitioning is simply producing records to an internal topic and consuming them back into the application.   By producing the records the updated keys land on
the correct partition. Additionally, since the key-value types have changed you need to provide updated `Serde` objects, via the `Grouped` configuration object
to Kafka Streams for the (de)serialization process for the repartitioning.

``` java
.aggregate(() -> new CountAndSum(0L, 0.0),
                        (key, value, aggregate) -> {
                            double sum = value + aggregate.sum();
                            long count = aggregate.count() + 1;
                            return new CountAndSum(count, sum);
                        },
                        Materialized.with(Serdes.String(), countAndSumSerde))

```

This aggregation performs a running average of movie ratings.  To enable this, it keeps the running sum and count of the ratings.  The [aggregate](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Materialized-) operator takes 3 parameters (there are overloads that accept [2](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-) and [4](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KGroupedStream.html#aggregate-org.apache.kafka.streams.kstream.Initializer-org.apache.kafka.streams.kstream.Aggregator-org.apache.kafka.streams.kstream.Named-org.apache.kafka.streams.kstream.Materialized-) parameters):

1. An initializer for the default value in this case a new instance of the `CountAndSum` object which is a Java pojo containing the count and sum of scores.
2. An [Aggregator](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Aggregator.html) instance which performs the aggregation action.  Here the code uses a Java [lambda expression](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html) instead of a concrete object instance.
3. A [Materialized](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/Materialized.html) object describing how the underlying [StateStore](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/processor/StateStore.html) is materialized.

``` java
                .toStream()
                .mapValues(value -> value.sum() / value.count())
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));

```
Aggregations in Kafka Streams return a [KTable](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KTable.html) instance, so it's converted to a [KStream](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html) then [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) calculates the average using the `CountAndSum` object emitted from the aggregation which is produced to an output topic. 

## Running the example

You can run the example backing this tutorial in one of two ways: locally with Kafka running in Docker, or with Confluent Cloud.
        
<details>
<summary>Running Kafka in Docker</summary>

### Prerequisites

* [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) 
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

### Start Kafka

* Execute `confluent local kafka start`  from a terminal window, and copy the `host:port` output
* Save the file `confluent.properties.orig` as `confluent.properties` (ignored by git) and update the `bootstrap.servers` config with the value from the previous step

### Create the topics `average-input` and `average-output`

*  `confluent local kafka topic create average-input`
*  `confluent local kafka topic create average-output`

### Start Kafka Streams

* CD into `aggregating-average/kstreams`
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-average-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/aggregating-average-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run one of the following commands to add some records for the Kafka Streams application to process

* `confluent local kafka topic produce average-input < src/main/resources/input.txt`

### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this
```text
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=45.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=100.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=65.5]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=50.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=80.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=90.5]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=75.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=90.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=101.1]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=62.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=85.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=97.5]]
```
Note that, because incoming records don't have keys, the `KStream.map` function sets the keys to enable the aggregation
Then roughly in 30 seconds you should see the average output like this:

```text
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Outgoing average key:[300] value:[56.55555555555556]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Outgoing average key:[410] value:[86.07777777777778]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Outgoing average key:[404] value:[93.88499999999999]
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

### Create the topics `average-input` and `average-output`

*_Note that if you create the cluster using the CLI plugin you can omit the cluster-id from the commands_*

* `confluent kafka topic create average-input --cluster <CLUSTER_ID>` 
* `confluent kafka topic create average-output --cluster <CLUSTER_ID>` 

### Start Kafka Streams

* CD into `aggregating-average/kstreams`
* Run `./gradlew clean build`
* Run `java -jar build/libs/aggregating-average-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/aggregating-average-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run one of the following commands to add some records for the Kafka Streams application to process

* `confluent kafka topic produce average-input --cluster <CLUSTER_ID> < src/main/resources/input.txt`
### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this
```text
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=45.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=100.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=65.5]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=50.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=80.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=90.5]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=75.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=90.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=101.1]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=300, rating=62.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=404, rating=85.0]]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Incoming record key:[] value:[MovieRating[id=410, rating=97.5]]
```
Note that incoming records don't have keys, the `KStream.map` function sets the keys to enable the aggregation
Then roughly in 30 seconds you should see the average output like this:

```text
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Outgoing average key:[300] value:[56.55555555555556]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Outgoing average key:[410] value:[86.07777777777778]
[running-average-application-92ce2fa6-f6c9-49f1-9b89-97a35efff41b-StreamThread-1] INFO io.confluent.developer.KafkaStreamsRunningAverage - Outgoing average key:[404] value:[93.88499999999999]
```
</details>



