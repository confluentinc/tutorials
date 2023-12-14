# Versioned KTables for temporal join accuracy

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

### Create the topics `stream-input-topic`, `table-input-topic` and `output-topic`

* `confluent local kafka topic create stream-input-topic`
* `confluent local kafka topic create table-input-topic`
* `confluent local kafka topic create output-topic`

### Start Kafka Streams

* CD into `versioned-ktables/kstreams`
* Run `./gradlew clean build`
* Run `java -jar build/libs/versioned-ktables-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/versioned-ktables-standalone.jar [path to props file]`

### View the results

The example itself generates samples records to demonstrate proper temporal join semantics with a versioned `KTable`. It generates a stream
of strings containing the first half of popular food combinations like `"peanut butter and"`. On the table side, the correct 
strings to complete the food combinations (`"jelly"`) are generated before the stream entries. There are also incorrect matches that
come later (e.g, `"sardines"`).

In a terminal window, observe with the Confluent CLI that the output topic contains the expected food combinations:

``` plaintext
confluent local kafka topic consume output-topic --from-beginning
```

This will yield output like:
``` plaintext
peanut butter and jelly
ham and eggs
cheese and crackers
tea and crumpets
coffee with cream
```
Enter `Ctrl-C` to exit the console consumer.

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

### Create the topics `stream-input-topic`, `table-input-topic` and `output-topic`

*_Note that if you create the cluster using the CLI plugin you can omit the cluster-id from the commands_*

* `confluent kafka topic create stream-input-topic --cluster <CLUSTER_ID>` 
* `confluent kafka topic create table-input-topic --cluster <CLUSTER_ID>` 
* `confluent kafka topic create output-topic --cluster <CLUSTER_ID>`

### Start Kafka Streams

* CD into `versioned-ktables/kstreams`
* Run `./gradlew clean build`
* Run `java -jar build/libs/versioned-ktables-standalone.jar`
* The command above assumes using the properties file `src/main/resources/confluent.properties` if you're using something else you'll need to add the path to the command i.e. `java -jar build/libs/versioned-ktables-standalone.jar [path to props file]`

### View the results

### View the results

The example itself generates samples records to demonstrate proper temporal join semantics with a versioned `KTable`. It generates a stream
of strings containing the first half of popular food combinations like `"peanut butter and"`. On the table side, the correct
strings to complete the food combinations (`"jelly"`) are generated before the stream entries. There are also incorrect matches that
come later (e.g, `"sardines"`).

In a terminal window, observe with the Confluent CLI that the output topic contains the expected food combinations:

``` plaintext
confluent local kafka topic consume output-topic --from-beginning
```

This will yield output like:
``` plaintext
peanut butter and jelly
ham and eggs
cheese and crackers
tea and crumpets
coffee with cream
```
Enter `Ctrl-C` to exit the console consumer.

</details>
