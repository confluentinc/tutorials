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
docker compose -f ./docker/docker-compose-kafka-sr.yml up -d
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
java -jar multiple-event-types-protobuf/kafka/build/libs/multiple-event-types-protobuf-standalone-0.0.1.jar \
    multiple-event-types-protobuf/kafka/local.properties
```

### Cleanup

Stop Kafka and Schema Registry:

```shell
docker compose -f ./docker/docker-compose-kafka-sr.yml down
```

</details>

<details>

<summary>Run in Confluent Cloud</summary>

### Prerequisites

- Java 17
- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

### Create Confluent Cloud resources

Log in to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial.

Note: You may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name kafka-multiple-event-types-env \
  --kafka-cluster-name kafka-multiple-event-types-cluster \
  --create-kafka-key \
  --create-sr-key \
  --kafka-java-properties-file multiple-event-types-protobuf/kafka/cloud.properties
```

The plugin should complete in under a minute.

## Create topic

Create the topic for the application:

```shell
confluent kafka topic create proto-events
```

### Build the application

Build the application uberjar:

```shell
./gradlew :multiple-event-types-protobuf:kafka:shadowJar
```

### Run the application

Run the application, which produces and consumes pageview and purchase events, with the following command. Note that we are passing the client configuration as an argument:

```shell
java -jar multiple-event-types-protobuf/kafka/build/libs/multiple-event-types-protobuf-standalone-0.0.1.jar \
    multiple-event-types-protobuf/kafka/cloud.properties
```

In the Confluent Cloud Console, select the `Messages` tab for the `proto-events` topic and view the messages that are produced.

### Clean up

When you are finished, delete the `kafka-multiple-event-types-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```

</details>
