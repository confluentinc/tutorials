<!-- title: How to join a KTable and a KTable in Kafka Streams -->
<!-- description: In this tutorial, learn how to join a KTable and a KTable in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to join a KTable and a KTable in Kafka Streams

Suppose you have a set of movies that have been released and a stream of ratings from moviegoers about how entertaining they are.  But you're only interested in the latest rating and since the `KTable` is an update stream, you'll want to use a `KTable` for the ratings.   In this tutorial, we'll write a program that joins the latest rating with content about the movie.

First you'll create a `KTable` for the reference movie data:
```java
KTable<Long, Movie> movieTable = builder.stream(MOVIE_INPUT_TOPIC,
                Consumed.with(Serdes.Long(), movieSerde))
        .peek((key, value) -> LOG.info("Incoming movies key[{}] value[{}]", key, value))
        .toTable(Materialized.with(Serdes.Long(), movieSerde));
```
Here you've started with a `KStream` so you can add a `peek` statement to view the incoming records, then convert it to a table with the `toTable` operator.  Otherwise, you could create the table directly with `StreamBuilder.table`. We assume that the underlying topic is keyed on the movie ID.

Then you'll create your `KTable` of ratings:
```java
  KTable<Long, Rating> ratingsTable = builder.stream(RATING_INPUT_TOPIC,
                Consumed.with(Serdes.Long(), ratingSerde))
        .map((key, rating) -> new KeyValue<>(rating.id(), rating))
        .toTable(Materialized.with(Serdes.Long(), ratingSerde));
```
We need to have the same ID as the table, so first we'll use a `KStream.map` operator to set the rating ID as the key, and then use the `KStream.toTable` to get a table.  The `Rating` class ID is the same ID as the movie.

Now you use a [ValueJoiner](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/ValueJoiner.html) specifying how to construct the joined value of the two tables:

```java
public class MovieRatingJoiner implements ValueJoiner<Rating, Movie, RatedMovie> {

  public RatedMovie apply(Rating rating, Movie movie) {
    return new RatedMovie(movie.id(), movie.title(), movie.releaseYear(), rating.rating());
  }
}
```
Now, you'll put all this together using a `KTable.join` operation:

```java
  ratingsTable.join(movieTable, joiner, Materialized.with(Serdes.Long(), ratedMovieSerde))
        .toStream()
        .to(RATED_MOVIES_OUTPUT,
            Produced.with(Serdes.Long(), ratedMovieSerde));
```
Notice that you're supplying the `Serde` for the key, and the value of the joined result via the `Materialized` configuration object.
Also, to produce the joined results to Kafka, we need to convert the `KTable` into a `KStream` vi the `KTable.toStream()` method.

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
  --environment-name kafka-streams-table-table-join-env \
  --kafka-cluster-name kafka-streams-table-table-join-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./joining-table-table/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create movie-input
confluent kafka topic create ratings-input
confluent kafka topic create rated-movies-output
```

Start a console producer:

```shell
confluent kafka topic produce movie-input --parse-key --delimiter :
```

Enter a few JSON-formatted movie objects:

```plaintext
294:{"id":"294", "title":"Die Hard", "releaseYear":1988}
354:{"id":"354", "title":"Tree of Life", "releaseYear":2011}
782:{"id":"782", "title":"A Walk in the Clouds", "releaseYear":1998}
128:{"id":"128", "title":"The Big Lebowski", "releaseYear":1998}
780:{"id":"780", "title":"Super Mario Bros.", "releaseYear":1993}
```

Enter `Ctrl+C` to exit the console producer.

Similarly, start a console producer for movie ratings:

```shell
confluent kafka topic produce ratings-input --parse-key --delimiter :
```

Enter a few JSON-formatted ratings:

```plaintext
294:{"id":"294", "rating":8.2}
294:{"id":"294", "rating":8.5}
354:{"id":"354", "rating":9.9}
354:{"id":"354", "rating":9.7}
782:{"id":"782", "rating":7.8}
782:{"id":"782", "rating":7.7}
128:{"id":"128", "rating":8.7}
128:{"id":"128", "rating":8.4}
780:{"id":"780", "rating":2.1}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew joining-table-table:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd joining-table-table/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/kstreams-table-table-standalone.jar \
    io.confluent.developer.JoinTableToTable \
    ./src/main/resources/cloud.properties
```

Validate that you see enriched movie ratings in the `rated-movies-output` topic.


```shell
confluent kafka topic consume rated-movies-output -b
```

You should see the following. Note that you only see the latest rating per movie:

```shell
{"id":"294","title":"Die Hard","releaseYear":1988,"rating":8.5}
{"id":"354","title":"Tree of Life","releaseYear":2011,"rating":9.7}
{"id":"782","title":"A Walk in the Clouds","releaseYear":1998,"rating":7.7}
{"id":"128","title":"The Big Lebowski","releaseYear":1998,"rating":8.4}
{"id":"780","title":"Super Mario Bros.","releaseYear":1993,"rating":2.1}
```

## Clean up

When you are finished, delete the `kafka-streams-table-table-join-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic movie-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic ratings-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic rated-movies-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic movie-input \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted movie objects:

  ```plaintext
  294:{"id":"294", "title":"Die Hard", "releaseYear":1988}
  354:{"id":"354", "title":"Tree of Life", "releaseYear":2011}
  782:{"id":"782", "title":"A Walk in the Clouds", "releaseYear":1998}
  128:{"id":"128", "title":"The Big Lebowski", "releaseYear":1998}
  780:{"id":"780", "title":"Super Mario Bros.", "releaseYear":1993}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  Similarly, start a console producer for movie ratings:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic ratings-input \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted ratings:

  ```plaintext
  294:{"id":"294", "rating":8.2}
  294:{"id":"294", "rating":8.5}
  354:{"id":"354", "rating":9.9}
  354:{"id":"354", "rating":9.7}
  782:{"id":"782", "rating":7.8}
  782:{"id":"782", "rating":7.7}
  128:{"id":"128", "rating":8.7}
  128:{"id":"128", "rating":8.4}
  780:{"id":"780", "rating":2.1}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew joining-table-table:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd joining-table-table/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/kstreams-table-table-standalone.jar \
      io.confluent.developer.JoinTableToTable \
      ./src/main/resources/local.properties
  ```

  Validate that you see enriched movie ratings in the `rated-movies-output` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic rated-movies-output --from-beginning
  ```

  You should see the following. Note that you only see the latest rating per movie:

  ```shell
  {"id":"294","title":"Die Hard","releaseYear":1988,"rating":8.5}
  {"id":"354","title":"Tree of Life","releaseYear":2011,"rating":9.7}
  {"id":"782","title":"A Walk in the Clouds","releaseYear":1998,"rating":7.7}
  {"id":"128","title":"The Big Lebowski","releaseYear":1998,"rating":8.4}
  {"id":"780","title":"Super Mario Bros.","releaseYear":1993,"rating":2.1}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
