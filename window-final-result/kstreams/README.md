# Kafka Streams Final Result

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

## Running the example

You can run the example in this tutorial in one of two ways: locally with Kafka running in Docker, or with Confluent Cloud.
        
<details>
<summary>Running Kafka in Docker</summary>

### Prerequisites

* [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) 
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

### Start Kafka

* Execute `confluent local kafka start`  from a terminal window, and copy the `host:port` output
* Save the file `confluent.properties.orig` as `confluent.properties` (ignored by git) and update the `bootstrap.servers` config with the value from the previous step

### Create the topics `final-result-input` and `final-result-output`

*  `confluent local kafka topic create final-result-input`
*  `confluent local kafka topic create final-result-output`

### Start Kafka Streams

* CD into `window-final-result/kstreams`
* Run `./gradlew clean build`
* Run `java -jar build/libs/window-final-result-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/window-final-result-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run one of the following commands to add some records for the Kafka Streams application to process

* `confluent local kafka topic produce final-result-input < src/main/resources/input.txt`

### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this
``` plaintext
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=101, dateTime=2019-09-21T05:30:01.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=101, dateTime=2019-09-21T05:30:02.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=101, dateTime=2019-09-21T05:30:03.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=102, dateTime=2019-09-21T05:30:01.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=102, dateTime=2019-09-21T05:30:02.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=102, dateTime=2019-09-21T05:30:03.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=103, dateTime=2019-09-21T05:30:01.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=103, dateTime=2019-09-21T05:30:02.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=103, dateTime=2019-09-21T05:30:03.+0200, pressure=100]]
```
Note that, because incoming records don't have keys, the `KStream.selectKey` function sets the keys to enable the aggregation.
In roughly 30 seconds, you should see the count per window (3) output like this:

``` plaintext
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Outgoing count key:[[101@1569036600000/1569036610000]] value:[3]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Outgoing count key:[[102@1569036600000/1569036610000]] value:[3]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Outgoing count key:[[103@1569036600000/1569036610000]] value:[3]
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

* In the Confluent Cloud Console, click on the `Clients` option in the left-hand menu.
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

### Create the topics `final-result-input` and `final-result-output`

*_Note that if you create the cluster using the CLI plugin you can omit the cluster-id from the commands_*

* `confluent kafka topic create final-result-input --cluster <CLUSTER_ID>` 
* `confluent kafka topic create final-result-output --cluster <CLUSTER_ID>` 

### Start Kafka Streams

* CD into `window-final-result/kstreams`
* Run `./gradlew clean build`
* Run `java -jar build/libs/window-final-result-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/window-final-result-standalone.jar [path to props file]`

### Provide input records

Open another terminal window and run one of the following commands to add some records for the Kafka Streams application to process

* `confluent kafka topic produce final-result-input --cluster <CLUSTER_ID> < src/main/resources/input.txt`
### View the results

Go back to the terminal window running the Kafka Streams application you should see something like this
```text
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=101, dateTime=2019-09-21T05:30:01.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=101, dateTime=2019-09-21T05:30:02.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=101, dateTime=2019-09-21T05:30:03.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=102, dateTime=2019-09-21T05:30:01.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=102, dateTime=2019-09-21T05:30:02.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=102, dateTime=2019-09-21T05:30:03.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=103, dateTime=2019-09-21T05:30:01.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=103, dateTime=2019-09-21T05:30:02.+0200, pressure=100]]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Incoming record value[PressureAlert[id=103, dateTime=2019-09-21T05:30:03.+0200, pressure=100]]
```

Note that, because incoming records don't have keys, the `KStream.selectKey` function sets the keys to enable the aggregation.
In roughly 30 seconds, you should see the count per window (3) output like this:

```text
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Outgoing count key:[[101@1569036600000/1569036610000]] value:[3]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Outgoing count key:[[102@1569036600000/1569036610000]] value:[3]
[window-final-result-application-d612062d-64af-4690-a0fd-02d95d7bc9a0-StreamThread-1] INFO io.confluent.developer.WindowFinalResult - Outgoing count key:[[103@1569036600000/1569036610000]] value:[3]
```
</details>
