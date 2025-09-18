<!-- title: How to filter messages in a Kafka topic with Kafka Streams -->
<!-- description: In this tutorial, learn how to filter messages in a Kafka topic with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to filter messages in a Kafka topic with Kafka Streams

Consider a topic with events, and you want to filter out records not matching a given attribute.

```java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), publicationSerde))
        .filter((name, publication) -> "George R. R. Martin".equals(publication.name()))
        .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), publicationSerde));
```

To keep only records in the event stream matching a given predicate (either the key or the value), you'll use the [KStream.filter](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#filter(org.apache.kafka.streams.kstream.Predicate)).  
For retaining records that 
__*do not*__ match a predicate you can use [KStream.filterNot](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#filterNot(org.apache.kafka.streams.kstream.Predicate))

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
  --environment-name kafka-streams-filtering-env \
  --kafka-cluster-name kafka-streams-filtering-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./filtering/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create filtering-input
confluent kafka topic create filtering-output
```

Start a console producer:

```shell
confluent kafka topic produce filtering-input
```

Enter a few JSON-formatted books:

```plaintext
{"name":"George R. R. Martin", "title":"A Song of Ice and Fire"}
{"name":"C.S. Lewis", "title":"The Silver Chair"}
{"name":"C.S. Lewis", "title":"Perelandra"}
{"name":"George R. R. Martin", "title":"Fire & Blood"}
{"name":"J. R. R. Tolkien", "title":"The Hobbit"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew filtering:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd filtering/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/kstreams-filter-standalone.jar \
    io.confluent.developer.FilterEvents \
    ./src/main/resources/cloud.properties
```

Validate that you see only the books by George R. R. Martin in the `filtering-output` topic.

```shell
confluent kafka topic consume filtering-output -b
```

You should see:

```shell
{"name":"George R. R. Martin","title":"A Song of Ice and Fire"}
{"name":"George R. R. Martin","title":"Fire & Blood"}
```

## Clean up

When you are finished, delete the `kafka-streams-filtering-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic filtering-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic filtering-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic filtering-input
  ```

  Enter a few JSON-formatted books:

  ```plaintext
  {"name":"George R. R. Martin", "title":"A Song of Ice and Fire"}
  {"name":"C.S. Lewis", "title":"The Silver Chair"}
  {"name":"C.S. Lewis", "title":"Perelandra"}
  {"name":"George R. R. Martin", "title":"Fire & Blood"}
  {"name":"J. R. R. Tolkien", "title":"The Hobbit"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew filtering:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd filtering/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/kstreams-filter-standalone.jar \
      io.confluent.developer.FilterEvents \
      ./src/main/resources/local.properties
  ```

  Validate that you see only the books by George R. R. Martin in the `filtering-output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic filtering-output --from-beginning
  ```

  You should see:

  ```shell
  {"name":"George R. R. Martin","title":"A Song of Ice and Fire"}
  {"name":"George R. R. Martin","title":"Fire & Blood"}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
