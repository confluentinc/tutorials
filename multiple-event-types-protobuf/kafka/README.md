<!-- title: How to handle multiple event types in a Kafka topic with Protobuf -->
<!-- description: In this tutorial, learn how to handle multiple event types in a Kafka topic with Protobuf, with step-by-step instructions and supporting code. -->

# How to handle multiple event types in a Kafka topic with Protobuf

It's sometimes advantageous to produce distinct but related event types to the same topic, e.g., to guarantee the exact order of different events for the same key.
For example, consider pageview and purchase records associated with the same customer ID. In order to properly attribute purchases to preceding pageviews, these distinct events must be sent to the same topic so that the order is preserved in one Kafka topic partition.
But, let's say we also need to maintain the topic-name subject constraints with Schema Registry.

To accomplish this with Protobuf-formatted events, we can use schema references, where a schema contains a field whose type is a reference to another schema.

## Example Protobuf schema with references

The example in this tutorial uses a top-level Protobuf schema specifying that a record is either a purchase or a pageview:

```noformat
message CustomerEvent {
  oneof action {
    Purchase purchase = 1;
    Pageview pageview = 2;
  }
  string id = 3;
}
```

Where these references are defined as follows:

```noformat
message Pageview {
  string url = 1;
  bool is_special = 2;
  string customer_id = 3;
}
```

```json
message Purchase {
  string item = 1;
  double amount = 2;
  string customer_id = 3;
}
```

Now, if you use the top-level schema for a topic, then you can produce either `PageviewProto.Pageview` or
`PurchaseProto.Purchase` records to the topic.

## Running the example

In order to run this example, first clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

Now you can either execute the unit test included with the example, or run the example in Docker or in Confluent Cloud.

<details>
<summary>Execute the unit tests</summary>

To run the unit tests, use the provided Gradle Wrapper:

```shell
./gradlew clean :multiple-event-types-protobuf:kafka:test --info  
```

</details>

<details>

<summary>Run in Docker</summary>

### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

### Start Kafka and Schema Registry

Start Kafka by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb-kraft-cluster.yml up -d
```

### Create topic

Create the `proto-events` topic:

```shell
docker exec -t broker kafka-topics --create --topic proto-events --bootstrap-server broker:9092
```

### Build the application

Build the application uberjar:

```shell
./gradlew :multiple-event-types-protobuf:kafka:shadowJar
```

### Run the application

Run the application, which produces and consumes pageview and purchase events, with the following command:

```shell
java -jar multiple-event-types-protobuf/kafka/build/libs/multiple-event-types-protobuf-standalone-0.0.1.jar multiple-event-types-protobuf/kafka/local.properties
```

### Cleanup

Stop Kafka and Schema Registry:

```shell
docker compose -f ./docker/docker-compose-ksqldb-kraft-cluster.yml down
```

</details>

<details>

<summary>Run in Confluent Cloud</summary>

### Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account and a Kafka cluster created within it ([quick start](https://docs.confluent.io/cloud/current/get-started/index.html)).

### Create topic

Using the Confluent Cloud Console, create a topic with default settings called `proto-events`.

### Generate client configuration

In the Confluent Cloud Console, navigate to the Cluster Overview page. Select `Clients` in the lefthand navigation and create a new `Java` client. Generate API keys during this step, and download the generated client configuration. Place it at `multiple-event-types-protobuf/kafka/cloud.properties`.

### Register schemas

Run the following task to register the schemas in Schema Registry:

```shell
./gradlew :multiple-event-types-protobuf:kafka:registerSchemasTask
```

In the Confluent Cloud Console, navigate to `Topics` in the lefthand navigation, select the `proto-events` topic, and click `Schema`. Validate that a `Value` schema has been set.

### Build the application

Build the application uberjar:

```shell
./gradlew :multiple-event-types-protobuf:kafka:shadowJar
```

### Run the application

Run the application, which produces and consumes pageview and purchase events, with the following command. Note that we are passing the client configuration as an argument:

```shell
java -jar multiple-event-types-protobuf/kafka/build/libs/multiple-event-types-protobuf-standalone-0.0.1.jar multiple-event-types-protobuf/kafka/cloud.properties
```

In the Confluent Cloud Console, select the `Messages` tab for the `proto-events` topic and view the messages that are produced.

### Cleanup

Delete the cluster used for this tutorial if you no longer need it.

</details>
