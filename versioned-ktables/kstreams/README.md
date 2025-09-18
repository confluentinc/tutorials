<!-- title: How to achieve temporal join accuracy in Kafka Streams with versioned KTables -->
<!-- description: In this tutorial, learn how to achieve temporal join accuracy in Kafka Streams with versioned KTables, with step-by-step instructions and supporting code. -->

# How to achieve temporal join accuracy in Kafka Streams with versioned KTables

Proper handling of time in Kafka Stream stream-table joins has historically been difficult to achieve. It used to be when 
Kafka Streams executes a stream-table join the stream side event would join the latest available record with the same key on the table side.
But, sometimes it's important for the stream event to match up with a table record by timestamp as well as key.
Consider a stream of stock transactions and a table of stock prices -- it's essential the transaction joins with the 
stock price at the time of the transaction, not the latest price. A versioned state store tracks multiple record versions 
for the same key, rather than the single latest record per key, as is the case for standard non-versioned stores.

The key to versioned state stores is to use a `VersionedKeyValueStore` when creating a `KTable`:
``` java annotate
    final VersionedBytesStoreSupplier versionedStoreSupplier =
              Stores.persistentVersionedKeyValueStore("versioned-ktable-store",
                                                       Duration.ofMinutes(10));


    final KTable<String, String> tableInput = builder.table(tableInputTopic,
                Materialized.<String, String>as(versionedStoreSupplier)
                        .withKeySerde(stringSerde)
                        .withValueSerde(stringSerde));
```
Assuming you have a versioned `KTable` and a `KStream` with out-of-order records to join, the join will be temporally correct since each stream record with be joined
with a table record _aligned by timestamp_ instead of simply using the latest record for the key.

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
  --environment-name kafka-streams-versioned-ktable-join-env \
  --kafka-cluster-name kafka-streams-versioned-ktable-join-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./versioned-ktables/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create stream-input-topic
confluent kafka topic create table-input-topic
confluent kafka topic create output-topic
```

Start a console producer:

```shell
confluent kafka topic produce table-input-topic --parse-key --delimiter :
```

Enter a few strings representing the second half of common phrases:

```plaintext
one:jelly
two:cheese
three:crackers
four:biscuits
five:cream
```

Start a console producer:

```shell
confluent kafka topic produce stream-input-topic --parse-key --delimiter :
```

Enter a few strings representing the correct first half of the common phrases:

```plaintext
one:peanut butter and
two:ham and
three:cheese and
four:tea and
five:coffee with
```

Enter `Ctrl+C` to exit the console producer.

Finally, start a console producer:

```shell
confluent kafka topic produce table-input-topic --parse-key --delimiter :
```

Enter a few strings representing the _incorrect_ second half of common phrases:

```plaintext
one:sardines
two:an old tire
three:fish eyes
four:moldy bread
five:lots of salt
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew versioned-ktables:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd versioned-ktables/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/versioned-ktables-standalone.jar \
    io.confluent.developer.VersionedKTableExample \
    ./src/main/resources/cloud.properties
```

Validate that you see the correct phrases in the `output-topic` topic.

```shell
confluent kafka topic consume output-topic -b
```

You should see:

```shell
peanut butter and jelly
ham and cheese
cheese and crackers
tea and biscuits
coffee with cream
```

## Clean up

When you are finished, delete the `kafka-streams-versioned-ktable-join-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic stream-input-topic
  kafka-topics --bootstrap-server localhost:9092 --create --topic table-input-topic
  kafka-topics --bootstrap-server localhost:9092 --create --topic output-topic
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic table-input-topic \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few strings representing the second half of common phrases:

  ```plaintext
  one:jelly
  two:cheese
  three:crackers
  four:biscuits
  five:cream
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic stream-input-topic \
      --property "parse.key=true" --property "key.separator=:"
  ```

  Enter a few strings representing the correct first half of the common phrases:

  ```plaintext
  one:peanut butter and
  two:ham and
  three:cheese and
  four:tea and
  five:coffee with
  ```

  Enter `Ctrl+C` to exit the console producer.

  Finally, start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic table-input-topic \
      --property "parse.key=true" --property "key.separator=:"
  ```
  Enter a few strings representing the _incorrect_ second half of common phrases:

  ```plaintext
  one:sardines
  two:an old tire
  three:fish eyes
  four:moldy bread
  five:lots of salt
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew versioned-ktables:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd versioned-ktables/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/versioned-ktables-standalone.jar \
      io.confluent.developer.VersionedKTableExample \
      ./src/main/resources/local.properties
  ```

  Validate that you see the correct phrases in the `output-topic` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic output-topic --from-beginning
  ```

  You should see the correct phrases:

  ```shell
  peanut butter and jelly
  ham and cheese
  cheese and crackers
  tea and biscuits
  coffee with cream
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
