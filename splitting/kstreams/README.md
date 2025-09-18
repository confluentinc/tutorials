<!-- title: How to split a stream of events into substreams with Kafka Streams -->
<!-- description: In this tutorial, learn how to split a stream of events into substreams with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to split a stream of events into substreams with Kafka Streams

If you have a stream of events in a Kafka topic and wish to route those events to different topics based on data in the events, [KStream.split](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#split()) and [BranchedKStream.branch](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#split()) can be used to route the source topic's events.

For example, suppose that you have a Kafka topic representing appearances of an actor or actress in a film, with each event also containing the movie genre. The following topology definition will split the stream into three substreams: one containing drama events, one containing fantasy, and one containing events for all other genres:

``` java
    builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), actingEventSerde))
            .split()
            .branch((key, appearance) -> "drama".equals(appearance.genre()),
                    Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_DRAMA)))
            .branch((key, appearance) -> "fantasy".equals(appearance.genre()),
                    Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_FANTASY)))
            .branch((key, appearance) -> true,
                    Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_OTHER)));
```

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
  --environment-name kafka-streams-splitting-env \
  --kafka-cluster-name kafka-streams-splitting-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./splitting/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create acting-events
confluent kafka topic create acting-events-drama
confluent kafka topic create acting-events-fantasy
confluent kafka topic create acting-events-other
```

Start a console producer:

```shell
confluent kafka topic produce acting-events
```

Enter a few JSON-formatted acting events:

```plaintext
{"name":"Meryl Streep", "title":"The Iron Lady", "genre":"drama"}
{"name":"Matt Damon", "title":"The Martian", "genre":"drama"}
{"name":"Judy Garland", "title":"The Wizard of Oz", "genre":"fantasy"}
{"name":"Jennifer Aniston", "title":"Office Space", "genre":"comedy"}
```

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew splitting:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd splitting/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/splitting-standalone.jar \
    io.confluent.developer.SplitStream \
    ./src/main/resources/cloud.properties
```

Validate that you see the drama acting events in the `acting-events-drama` topic.


```shell
confluent kafka topic consume acting-events-drama -b
```

You should see only the two drama events:

```shell
{"name":"Matt Damon","title":"The Martian","genre":"drama"}
{"name":"Meryl Streep","title":"The Iron Lady","genre":"drama"}
```

## Clean up

When you are finished, delete the `kafka-streams-splitting-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic acting-events
  kafka-topics --bootstrap-server localhost:9092 --create --topic acting-events-drama
  kafka-topics --bootstrap-server localhost:9092 --create --topic acting-events-fantasy
  kafka-topics --bootstrap-server localhost:9092 --create --topic acting-events-other
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic acting-events
  ```

  Enter a few JSON-formatted acting events:

  ```plaintext
  {"name":"Meryl Streep", "title":"The Iron Lady", "genre":"drama"}
  {"name":"Matt Damon", "title":"The Martian", "genre":"drama"}
  {"name":"Judy Garland", "title":"The Wizard of Oz", "genre":"fantasy"}
  {"name":"Jennifer Aniston", "title":"Office Space", "genre":"comedy"}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew splitting:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd splitting/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/splitting-standalone.jar \
      io.confluent.developer.SplitStream \
      ./src/main/resources/local.properties
  ```

  Validate that you see the drama acting events in the `acting-events-drama` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic acting-events-drama --from-beginning
  ```

  You should see only the two drama events:

  ```shell
  {"name":"Matt Damon","title":"The Martian","genre":"drama"}
  {"name":"Meryl Streep","title":"The Iron Lady","genre":"drama"}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
