# Cogrouping in Kafka Streams

Cogrouping is combining an aggregate, like `count`, from multiple streams into a single result.
In this tutorial, you will compute the count of user login events per application in your system, grouping the individual result from each source stream into one aggregated object using Kafka Streams [Cogroup](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/CogroupedKStream.html#cogroup-org.apache.kafka.streams.kstream.KGroupedStream-org.apache.kafka.streams.kstream.Aggregator-) functionality.

```java
final KGroupedStream<String, LoginEvent> appOneGrouped =
        builder.stream(APP_ONE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appTwoGrouped =
        builder.stream(APP_TWO_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appThreeGrouped =
        builder.stream(APP_THREE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();

final Aggregator<String, LoginEvent, LoginRollup> loginAggregator = new LoginAggregator();

        appOneGrouped.cogroup(loginAggregator)
                .cogroup(appTwoGrouped, loginAggregator)
                .cogroup(appThreeGrouped, loginAggregator)
                .aggregate(() -> new LoginRollup(new HashMap<>()), Materialized.with(Serdes.String(), loginRollupSerde))
        .toStream()
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, loginRollupSerde));

```

Let's review the key points in this example

```java
final KGroupedStream<String, LoginEvent> appOneGrouped =
        builder.stream(APP_ONE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appTwoGrouped =
        builder.stream(APP_TWO_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appThreeGrouped =
        builder.stream(APP_THREE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
```

You have three input streams, and you need the intermediate object `KGroupedStream`, 
so you execute the `groupByKey()` method on each stream. 
For this tutorial, we have assumed the incoming records already have keys.

```java
Aggregator<String, LoginEvent, LoginRollup> loginAggregator = new LoginAggregator();
```
You’re using the cogrouping functionality here to get an overall grouping of logins per application. 
Kafka Streams creates this total grouping by using an `Aggregator` who knows how to extract records from each grouped stream. 
Your `Aggregator` instance here knows how to correctly combine each `LoginEvent` into the larger `LoginRollup` object.

```java
   appOneGrouped.cogroup(loginAggregator)
                .cogroup(appTwoGrouped, loginAggregator)
                .cogroup(appThreeGrouped, loginAggregator)
                .aggregate(() -> new LoginRollup(new HashMap<>()), Materialized.with(Serdes.String(), loginRollupSerde))
```

Now with your `KGroupedStream` objects, you start creating your larger aggregate by calling `KGroupedStream.cogroup()` 
on the first stream, using your `Aggregator`. This first step returns a `CogroupedKStream` instance. 
Then for each remaining `KGroupedStream`, you execute `CogroupedKSteam.cogroup()` using 
one of the `KGroupedStream` instances and the `Aggregator` you created previously. 
You repeat this sequence of calls for all the `KGroupedStream` objects you want to combine into an overall aggregate.

```java
       .toStream()
       .to(OUTPUT_TOPIC, Produced.with(stringSerde, loginRollupSerde));
```

The `aggregate` operation returns a `KTable` so you'll convert it to a `KStream` for producing the results out to a Kafka topic.

```java
public LoginRollup apply(final String appId,
                           final LoginEvent loginEvent,
                           final LoginRollup loginRollup) {
    final String userId = loginEvent.userId();
    final Map<String, Map<String, Long>> allLogins = loginRollup.loginByAppIdAndUserId();
    final Map<String, Long> userLogins = allLogins.computeIfAbsent(appId, key -> new HashMap<>());
    userLogins.compute(userId, (k, v) -> v == null ? 1L : v + 1L);
    return loginRollup;
```
The `Aggregator` you saw in the previous step constructs a map of maps: the count of logins per user, per application. 
What you see here is the core logic of the `LoginAggregator`.
Each call to `Aggregator.apply` retrieves the user login map for 
the given application id (or creates one if it doesn’t exist). 
From there, the `Aggregator` increments the login count for the given user.   

## Running the example

<details>
  <summary>Running Kafka in Docker</summary>

* [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

### Start Kafka

* Execute ```confluent local kafka start```  from a terminal window, and copy the `host:port` output
* Save the file `confluent.properties.orig` as `confluent.properties` (ignored by git) and update the `bootstrap.servers` config with the value from the previous step

### Create the topics `app-one-input`, `app-two-input`, `app-three-input`, and `cogrouping-output`

* `confluent local kafka topic create app-one-input`
* `confluent local kafka topic create app-two-input`
* `confluent local kafka topic create app-three-input`
* `confluent local kafka topic create cogrouping-output`

### Provide input records

Open a terminal window and run the following commands to add some records for the Kafka Streams application to process.

* `confluent local kafka topic produce app-one-input --parse-key --delimiter "#" < src/main/resources/input-one.txt`
* `confluent local kafka topic produce app-two-input --parse-key --delimiter "#" < src/main/resources/input-two.txt`
* `confluent local kafka topic produce app-three-input --parse-key --delimiter "#" < src/main/resources/input-three.txt`

### Start Kafka Streams

* CD into the `cogrouping-streams/kstreams` directory
* Run `./gradlew clean build`
* Run `java -jar build/libs/cogrouping-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/cogrouping-standalone.jar [path to props file]`

### View the results

In the terminal window running the Kafka Streams application, you should see something like this:
```text
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Ted, time=123456]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Ted, time=123457]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Carol, time=123458]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Carol, time=123459]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Alice, time=123467]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Carol, time=123468]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Bob, time=123456]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Carol, time=123457]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Ted, time=123458]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Carol, time=123459]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Bob, time=123456]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Alice, time=123457]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Alice, time=123458]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Carol, time=123459]]


```
Then roughly in 30 seconds you should see the cogrouping output like this:

```text
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - CoGrouping results key["one"] value[LoginRollup[loginByAppIdAndUserId={"one"={Ted=2, Carol=3, Alice=1}}]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - CoGrouping results key["two"] value[LoginRollup[loginByAppIdAndUserId={"two"={Bob=1, Carol=2, Ted=1}}]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - CoGrouping results key["three"] value[LoginRollup[loginByAppIdAndUserId={"three"={Bob=1, Alice=2, Carol=1}}]]
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

### Create the topics `app-one-input`, `app-two-input`, `app-three-input`, and `cogrouping-output`

*_Note that if you create the cluster using the CLI plugin you can omit the `--cluster` argument from the commands_*

* `confluent kafka topic create app-one-input --cluster <CLUSTER_ID>`
* `confluent kafka topic create app-two-input --cluster <CLUSTER_ID>`
* `confluent kafka topic create app-three-input --cluster <CLUSTER_ID>`
* `confluent kafka topic create cogrouping-output --cluster <CLUSTER_ID>`

### Provide input records

Open a terminal window and run the following commands to add some records for the Kafka Streams application to process.

* `confluent kafka topic produce app-one-input --parse-key --delimiter "#" --cluster <CLUSTER_ID> < src/main/resources/input-one.txt`
* `confluent kafka topic produce app-two-input --parse-key --delimiter "#" --cluster <CLUSTER_ID> < src/main/resources/input-two.txt`
* `confluent kafka topic produce app-three-input --parse-key --delimiter "#" --cluster <CLUSTER_ID> < src/main/resources/input-three.txt`

### Start Kafka Streams

* CD into the `cogrouping-streams/kstreams` directory
* Run `./gradlew clean build`
* Run `java -jar build/libs/cogrouping-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/cogrouping-standalone.jar [path to props file]`

### View the results

In the terminal window running the Kafka Streams application, you should see something like this:
```text
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Ted, time=123456]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Ted, time=123457]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Carol, time=123458]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Carol, time=123459]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Alice, time=123467]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App one input key["one"] value[LoginEvent[appId=one, userId=Carol, time=123468]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Bob, time=123456]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Carol, time=123457]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Ted, time=123458]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App two input key["two"] value[LoginEvent[appId=two, userId=Carol, time=123459]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Bob, time=123456]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Alice, time=123457]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Alice, time=123458]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - App three input key["three"] value[LoginEvent[appId=three, userId=Carol, time=123459]]


```
Then roughly in 30 seconds you should see the cogrouping output like this:

```text
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - CoGrouping results key["one"] value[LoginRollup[loginByAppIdAndUserId={"one"={Ted=2, Carol=3, Alice=1}}]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - CoGrouping results key["two"] value[LoginRollup[loginByAppIdAndUserId={"two"={Bob=1, Carol=2, Ted=1}}]]
[cogrouping-streams-5ee298ff-7b02-4718-8b7c-f87e1277c967-StreamThread-1] INFO io.confluent.developer.CogroupingStreams - CoGrouping results key["three"] value[LoginRollup[loginByAppIdAndUserId={"three"={Bob=1, Alice=2, Carol=1}}]]
```
</details>