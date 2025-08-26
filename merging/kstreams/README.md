<!-- title: How to merge multiple streams with Kafka Streams -->
<!-- description: In this tutorial, learn how to merge multiple streams with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to merge multiple streams with Kafka Streams

In this tutorial, we take a look at the case when you have multiple streams, but you'd like to merge them into one.  
We'll use multiple streams of a music catalog to demonstrate.

Here's the first stream of rock music:

```java
KStream<String, SongEvent> rockSongs = builder.stream(ROCK_MUSIC_INPUT, Consumed.with(stringSerde, songEventSerde));
```

And the stream of classical music:

```java
 KStream<String, SongEvent> classicalSongs = builder.stream(CLASSICAL_MUSIC_INPUT, Consumed.with(stringSerde, songEventSerde));
```

To merge these two streams you'll use the [KStream.merge](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KStream.html#merge-org.apache.kafka.streams.kstream.KStream-) operator:

```java
KStream<String, SongEvent> allSongs = rockSongs.merge(classicalSongs);
```
The `KStream.merge` method does not guarantee any order of the merged record streams.  The records maintain their ordering relative to the original source topic.

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
  --environment-name kafka-streams-merging-env \
  --kafka-cluster-name kafka-streams-merging-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./merging/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create rock-input
confluent kafka topic create classical-input
confluent kafka topic create all-music-output
```

Start a console producer:

```shell
confluent kafka topic produce rock-input
```

Enter a few JSON-formatted rock songs:

```plaintext
{"artist":"Metallica", "title":"Fade To Black"}
{"artist":"Van Halen", "title":"Jump"}
```

Enter `Ctrl+C` to exit the console producer.

Similarly, start a console producer for classical songs:

```shell
confluent kafka topic produce classical-input
```

Enter a few JSON-formatted classical songs:

```plaintext
{"artist":"Wolfgang Amadeus Mozart", "title":"The Magic Flute"}
{"artist":"Johann Pachelbel", "title":"Canon"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew merging:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd merging/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/kstreams-merge-standalone.jar \
    io.confluent.developer.MergeStreams \
    ./src/main/resources/cloud.properties
```

Validate that you see the drama acting events in the `all-music-output` topic.


```shell
confluent kafka topic consume all-music-output -b
```

You should see all songs:

```shell
{"artist":"Johann Pachelbel","title":"Canon"}
{"artist":"Van Halen","title":"Jump"}
{"artist":"Wolfgang Amadeus Mozart","title":"The Magic Flute"}
{"artist":"Metallica","title":"Fade To Black"}
```

## Clean up

When you are finished, delete the `kafka-streams-merging-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic rock-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic classical-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic all-music-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic rock-input
  ```

  Enter a few JSON-formatted rock songs:

  ```plaintext
  {"artist":"Metallica", "title":"Fade To Black"}
  {"artist":"Van Halen", "title":"Jump"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  Similarly, start a console producer for classical songs:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic classical-input
  ```

  Enter a few JSON-formatted classical songs:

  ```plaintext
  {"artist":"Wolfgang Amadeus Mozart", "title":"The Magic Flute"}
  {"artist":"Johann Pachelbel", "title":"Canon"}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew merging:kstreams:shadowJar
  ```

 Navigate into the application's home directory:

  ```shell
  cd merging/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/kstreams-merge-standalone.jar \
      io.confluent.developer.MergeStreams \
      ./src/main/resources/local.properties
  ```

 Validate that you see all songs in the `all-music-output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic all-music-output --from-beginning
  ```

 You should see all songs:

  ```shell
  {"artist":"Metallica","title":"Fade To Black"}
  {"artist":"Van Halen","title":"Jump"}
  {"artist":"Wolfgang Amadeus Mozart","title":"The Magic Flute"}
  {"artist":"Johann Pachelbel","title":"Canon"}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
