<!-- title: How to compute an aggregate from multiple streams using cogrouping in Kafka Streams -->
<!-- description: In this tutorial, learn how to compute an aggregate from multiple streams using cogrouping in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to compute an aggregate from multiple streams using cogrouping in Kafka Streams

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
  --environment-name kafka-streams-cogrouping-env \
  --kafka-cluster-name kafka-streams-cogrouping-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./cogrouping-streams/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create app-one-input
confluent kafka topic create app-two-input
confluent kafka topic create app-three-input
confluent kafka topic create cogrouping-output
```

Start a console producer:

```shell
confluent kafka topic produce app-one-input --parse-key --delimiter #
```

Enter a few JSON-formatted login events:

```plaintext
1#{"appId":"one", "userId":"foo", "time":5}
1#{"appId":"one", "userId":"bar", "time":6}
1#{"appId":"one", "userId":"bar", "time":7}
```

Enter `Ctrl+C` to exit the console producer.

Similarly, start a console producer for the second app's login events:

```shell
confluent kafka topic produce app-two-input --parse-key --delimiter #
```

Enter a few JSON-formatted login events:

```plaintext
2#{"appId":"two", "userId":"foo", "time":5}
2#{"appId":"two", "userId":"foo", "time":6}
2#{"appId":"two", "userId":"bar", "time":7}
```

Enter `Ctrl+C` to exit the console producer.

Finally, start a console producer for the third app's login events:

```shell
confluent kafka topic produce app-three-input --parse-key --delimiter #
```

Enter a few JSON-formatted login events:

```plaintext
3#{"appId":"three", "userId":"foo", "time":5}
3#{"appId":"three", "userId":"foo", "time":6}
3#{"appId":"three", "userId":"bar", "time":7}
3#{"appId":"three", "userId":"bar", "time":9}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew cogrouping-streams:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd cogrouping-streams/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/cogrouping-standalone.jar \
    io.confluent.developer.CogroupingStreams \
    ./src/main/resources/cloud.properties
```

Validate that you see all login counts by app and user ID in the `cogrouping-output` topic.

```shell
confluent kafka topic consume cogrouping-output -b
```

You should see:

```shell
{"loginByAppIdAndUserId":{"1":{"foo":1,"bar":2}}}
{"loginByAppIdAndUserId":{"2":{"foo":2,"bar":1}}}
{"loginByAppIdAndUserId":{"3":{"foo":2,"bar":2}}}
```

## Clean up

When you are finished, delete the `kafka-streams-cogrouping-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic app-one-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic app-two-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic app-three-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic cogrouping-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic app-one-input --property "parse.key=true" --property "key.separator=#"
  ```

  Enter a few JSON-formatted login events:

  ```plaintext
  1#{"appId":"one", "userId":"foo", "time":5}
  1#{"appId":"one", "userId":"bar", "time":6}
  1#{"appId":"one", "userId":"bar", "time":7}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  Similarly, start a console producer for the second app's login events:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic app-two-input --property "parse.key=true" --property "key.separator=#"
  ```

  Enter a few JSON-formatted login events:

  ```plaintext
  2#{"appId":"two", "userId":"foo", "time":5}
  2#{"appId":"two", "userId":"foo", "time":6}
  2#{"appId":"two", "userId":"bar", "time":7}
  ```

  Enter `Ctrl+C` to exit the console producer.

  Finally, start a console producer for the third app's login events:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic app-three-input --property "parse.key=true" --property "key.separator=#"
  ```

  Enter a few JSON-formatted login events:

  ```plaintext
  3#{"appId":"three", "userId":"foo", "time":5}
  3#{"appId":"three", "userId":"foo", "time":6}
  3#{"appId":"three", "userId":"bar", "time":7}
  3#{"appId":"three", "userId":"bar", "time":9}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew cogrouping-streams:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd cogrouping-streams/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/cogrouping-standalone.jar \
      io.confluent.developer.CogroupingStreams \
      ./src/main/resources/local.properties
  ```

  Validate that you see all login counts by app and user ID in the `cogrouping-output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic cogrouping-output --from-beginning
  ```

  You should see:

  ```shell
  {"loginByAppIdAndUserId":{"1":{"foo":1,"bar":2}}}
  {"loginByAppIdAndUserId":{"2":{"foo":2,"bar":1}}}
  {"loginByAppIdAndUserId":{"3":{"foo":2,"bar":2}}}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
