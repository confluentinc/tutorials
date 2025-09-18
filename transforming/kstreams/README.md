<!-- title: How to transform events with Kafka Streams -->
<!-- description: In this tutorial, learn how to transform events with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to transform events with Kafka Streams

If you have a stream of events in a Kafka topic and wish to transform a field in each event, you simply need to use the `KStream.map` method to process each event.

As a concrete example, consider a topic with events that represent movies. Each event has a single attribute that combines its title and its release year into a string. The following topology definition outputs these events to a new topic with title and release date turned into their own attributes.

``` java
  builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), rawMovieSerde))
    .map((key, rawMovie) -> new KeyValue<>(rawMovie.id(), convertRawMovie(rawMovie)))
    .to(OUTPUT_TOPIC, Produced.with(Serdes.Long(), movieSerde));
```

The [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) method transforms each record of the input stream into a new record in the output stream, with the movie ID serving as the key. (There is also a [mapValues](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#mapValues-org.apache.kafka.streams.kstream.ValueMapper-) method that can be used if you only need to transform record values.) The `convertRawMovie` method in this example splits on `::` since the attribute containing
the movie title and release year looks like `Tree of Life::2011`. The `Serde`'s included in this example use Jackson to serialize and deserialize POJOs.

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
  --environment-name kafka-streams-transforming-env \
  --kafka-cluster-name kafka-streams-transforming-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./transforming/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create raw-movies
confluent kafka topic create movies
```

Start a console producer:

```shell
confluent kafka topic produce raw-movies
```

Enter a few JSON-formatted movies in which the `title` field has the release year appended:

```plaintext
{"id":"294", "title":"Die Hard::1988", "genre":"action"}
{"id":"354", "title":"Tree of Life::2011", "genre":"drama"}
{"id":"782", "title":"A Walk in the Clouds::1995", "genre":"romance"}
{"id":"128", "title":"The Big Lebowski::1998", "genre":"comedy"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew transforming:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd transforming/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/transforming-standalone.jar \
    io.confluent.developer.TransformStream \
    ./src/main/resources/cloud.properties
```

Validate that you see movies with proper `title` and `release_year` fields in the `movies` topic.

```shell
confluent kafka topic consume movies -b
```

You should see:

```shell
{"id":294,"title":"Die Hard","release_year":1988,"genre":"action"}
{"id":354,"title":"Tree of Life","release_year":2011,"genre":"drama"}
{"id":782,"title":"A Walk in the Clouds","release_year":1995,"genre":"romance"}
{"id":128,"title":"The Big Lebowski","release_year":1998,"genre":"comedy"}
```

## Clean up

When you are finished, delete the `kafka-streams-transforming-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic raw-movies
  kafka-topics --bootstrap-server localhost:9092 --create --topic movies
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic raw-movies
  ```

  Enter a few JSON-formatted movies in which the `title` field has the release year appended:

  ```plaintext
  {"id":"294", "title":"Die Hard::1988", "genre":"action"}
  {"id":"354", "title":"Tree of Life::2011", "genre":"drama"}
  {"id":"782", "title":"A Walk in the Clouds::1995", "genre":"romance"}
  {"id":"128", "title":"The Big Lebowski::1998", "genre":"comedy"}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew transforming:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd transforming/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/transforming-standalone.jar \
      io.confluent.developer.TransformStream \
      ./src/main/resources/local.properties
  ```

  Validate that you see movies with proper `title` and `release_year` fields in the `movies` topic.

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic movies --from-beginning
  ```

  You should see:

  ```shell
  {"id":294,"title":"Die Hard","release_year":1988,"genre":"action"}
  {"id":354,"title":"Tree of Life","release_year":2011,"genre":"drama"}
  {"id":782,"title":"A Walk in the Clouds","release_year":1995,"genre":"romance"}
  {"id":128,"title":"The Big Lebowski","release_year":1998,"genre":"comedy"}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
