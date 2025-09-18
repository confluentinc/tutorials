<!-- title: How to join on a foreign key in Kafka Streams -->
<!-- description: In this tutorial, learn how to join on a foreign key in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to join on a foreign key in Kafka Streams

Suppose you are running an internet streaming music service where you offer albums or individual music tracks for sale. You'd like to track trends in listener preferences by joining the track purchases against the table of albums. The track purchase key doesn't align with the primary key for the album table, but since the value of the track purchase contains the ID of the album, you can extract the album ID from the track purchase and complete a foreign key join against the album table.

```java
final KTable<Long, Album> albums = builder.table(ALBUM_TOPIC, Consumed.with(longSerde, albumSerde));

final KTable<Long, TrackPurchase> trackPurchases = builder.table(USER_TRACK_PURCHASE_TOPIC, Consumed.with(longSerde, trackPurchaseSerde));

final MusicInterestJoiner trackJoiner = new MusicInterestJoiner();

final KTable<Long, MusicInterest> musicInterestTable = trackPurchases.join(albums,
                                                                           TrackPurchase::albumId,
                                                                           trackJoiner);
```

The foreign key join is made possible with the overloaded [KTable.join](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KTable.html#join-org.apache.kafka.streams.kstream.KTable-java.util.function.Function-org.apache.kafka.streams.kstream.ValueJoiner-) method that takes a [Java Function](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html) specifying how to extract the foreign from the right-side records to perform the join with the left-side table.

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
  --kafka-java-properties-file ./fk-joins/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create album-input
confluent kafka topic create track-purchase
confluent kafka topic create music-interest
```

Start a console producer:

```shell
confluent kafka topic produce album-input --parse-key --delimiter :
```

Enter a few JSON-formatted albums:

```plaintext
5:{"id":"5", "title":"Physical Graffiti", "genre":"Rock", "artist":"Led Zeppelin"}
6:{"id":"6", "title":"Highway to Hell", "genre":"Rock", "artist":"AC/DC"}
7:{"id":"7", "title":"Radio", "genre":"Hip hop", "artist":"LL Cool J"}
8:{"id":"8", "title":"King of Rock", "genre":"Rap rock", "artist":"Run-D.M.C"}
```

Enter `Ctrl+C` to exit the console producer.

Similarly, start a console producer for track purchases:

```shell
confluent kafka topic produce track-purchase --parse-key --delimiter :
```

Enter a few JSON-formatted purchases:

```plaintext
100:{"id":"100", "songTitle":"Houses Of The Holy", "albumId":"5", "price":0.99}
101:{"id":"101", "songTitle":"King Of Rock", "albumId":"8", "price":0.99}
102:{"id":"102", "songTitle":"Shot Down In Flames", "albumId":"6", "price":0.99}
103:{"id":"103", "songTitle":"Rock The Bells", "albumId":"7", "price":0.99}
104:{"id":"104", "songTitle":"Can You Rock It Like This", "albumId":"8", "price":0.99}
105:{"id":"105", "songTitle":"Highway To Hell", "albumId":"6", "price":0.99}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew fk-joins:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd fk-joins/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/fkjoins-standalone.jar \
    io.confluent.developer.FkJoinTableToTable \
    ./src/main/resources/cloud.properties
```

Validate that you see joined records in the `music-interest` topic:

```shell
confluent kafka topic consume music-interest -b
```

You should see the following:

```shell
{"id":"5-100","genre":"Rock","artist":"Led Zeppelin"}
{"id":"8-101","genre":"Rap rock","artist":"Run-D.M.C"}
{"id":"6-102","genre":"Rock","artist":"AC/DC"}
{"id":"7-103","genre":"Hip hop","artist":"LL Cool J"}
{"id":"8-104","genre":"Rap rock","artist":"Run-D.M.C"}
{"id":"6-105","genre":"Rock","artist":"AC/DC"}
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
  kafka-topics --bootstrap-server localhost:9092 --create --topic album-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic track-purchase
  kafka-topics --bootstrap-server localhost:9092 --create --topic music-interest
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic album-input \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted albums:

  ```plaintext
  5:{"id":"5", "title":"Physical Graffiti", "genre":"Rock", "artist":"Led Zeppelin"}
  6:{"id":"6", "title":"Highway to Hell", "genre":"Rock", "artist":"AC/DC"}
  7:{"id":"7", "title":"Radio", "genre":"Hip hop", "artist":"LL Cool J"}
  8:{"id":"8", "title":"King of Rock", "genre":"Rap rock", "artist":"Run-D.M.C"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  Similarly, start a console producer for track purchases:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic track-purchase \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few JSON-formatted purchases:

  ```plaintext
  100:{"id":"100", "songTitle":"Houses Of The Holy", "albumId":"5", "price":0.99}
  101:{"id":"101", "songTitle":"King Of Rock", "albumId":"8", "price":0.99}
  102:{"id":"102", "songTitle":"Shot Down In Flames", "albumId":"6", "price":0.99}
  103:{"id":"103", "songTitle":"Rock The Bells", "albumId":"7", "price":0.99}
  104:{"id":"104", "songTitle":"Can You Rock It Like This", "albumId":"8", "price":0.99}
  105:{"id":"105", "songTitle":"Highway To Hell", "albumId":"6", "price":0.99}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew fk-joins:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd fk-joins/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/fkjoins-standalone.jar \
      io.confluent.developer.FkJoinTableToTable \
      ./src/main/resources/local.properties
  ```

  Validate that you see joined records in the `music-interest` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic music-interest --from-beginning
  ```

  You should see the following:

  ```shell
  {"id":"5-100","genre":"Rock","artist":"Led Zeppelin"}
  {"id":"8-101","genre":"Rap rock","artist":"Run-D.M.C"}
  {"id":"6-102","genre":"Rock","artist":"AC/DC"}
  {"id":"7-103","genre":"Hip hop","artist":"LL Cool J"}
  {"id":"8-104","genre":"Rap rock","artist":"Run-D.M.C"}
  {"id":"6-105","genre":"Rock","artist":"AC/DC"}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
