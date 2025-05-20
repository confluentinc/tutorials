<!-- title: How to handle multiple event types in a Kafka topic with Avro -->
<!-- description: In this tutorial, learn how to handle multiple event types in a Kafka topic with Avro, with step-by-step instructions and supporting code. -->

# How to handle multiple event types in a Kafka topic with Avro

It's sometimes advantageous to produce distinct but related event types to the same topic, e.g., to guarantee the exact order of different events for the same key.
For example, consider pageview and purchase records associated with the same customer ID. In order to properly attribute purchases to preceding pageviews, these distinct events must be sent to the same topic so that the order is preserved in one Kafka topic partition.
But, let's say we also need to maintain the topic-name subject constraints with Schema Registry.

To accomplish this with Avro-formatted events, we can use schema references, where a schema contains a field whose type is a reference to another schema.

## Example Avro schema with references

The example in this tutorial uses a top-level Avro schema specifying that a record is either a purchase or a pageview:

```json
[
  "io.confluent.developer.avro.Purchase",
  "io.confluent.developer.avro.PageView"
]
```

Where these references are defined as follows:

```json
{
  "type":"record",
  "namespace": "io.confluent.developer.avro",
  "name":"Pageview",
  "fields": [
    {"name": "url", "type":"string"},
    {"name": "is_special", "type": "boolean"},
    {"name": "customer_id", "type":  "string"}
  ]
}
```

```json
{
  "type":"record",
  "namespace": "io.confluent.developer.avro",
  "name":"Purchase",
  "fields": [
    {"name": "item", "type":"string"},
    {"name": "amount", "type": "double"},
    {"name": "customer_id", "type": "string"}
  ]
}
```

Now, if you use the top-level schema for a topic, then you can produce either `io.confluent.developer.avro.Purchase` or
`io.confluent.developer.avro.Pageview` records to the topic.

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
./gradlew clean :multiple-event-types-avro:kafka:test --info  
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
docker compose -f ./docker/docker-compose-ksqldb.yml up -d
```

### Create topic

Create the `avro-events` topic:

```shell
docker exec -t broker kafka-topics --create --topic avro-events --bootstrap-server broker:9092
```

### Register schemas

Run the following task to register the schemas in Schema Registry:

```shell
./gradlew :multiple-event-types-avro:kafka:registerSchemasTask
```

### Build the application

Build the application uberjar:

```shell
./gradlew :multiple-event-types-avro:kafka:shadowJar
```

### Run the application

Run the application, which produces and consumes pageview and purchase events, with the following command:

```shell
java -jar multiple-event-types-avro/kafka/build/libs/multiple-event-types-avro-standalone-0.0.1.jar multiple-event-types-avro/kafka/local.properties
```

### Cleanup

Stop Kafka and Schema Registry:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```

</details>

<details>

<summary>Run in Confluent Cloud</summary>

### Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account and a Kafka cluster created within it ([quick start](https://docs.confluent.io/cloud/current/get-started/index.html)).

### Create topic

Using the Confluent Cloud Console, create a topic with default settings called `avro-events`.

### Generate client configuration

In the Confluent Cloud Console, navigate to the Cluster Overview page. Select `Clients` in the left-hand navigation and create a new `Java` client. Generate API keys during this step, and download the generated client configuration. Place it at `multiple-event-types-avro/kafka/cloud.properties`.

### Register schemas

Run the following task to register the schemas in Schema Registry:

```shell
./gradlew :multiple-event-types-avro:kafka:registerSchemasTask
```

In the Confluent Cloud Console, navigate to `Topics` in the left-hand navigation, select the `avro-events` topic, and click `Schema`. Validate that a `Value` schema has been set.

### Build the application

Build the application uberjar:

```shell
./gradlew :multiple-event-types-avro:kafka:shadowJar
```

### Run the application

Run the application, which produces and consumes pageview and purchase events, with the following command. Note that we are passing the client configuration as an argument:

```shell
java -jar multiple-event-types-avro/kafka/build/libs/multiple-event-types-avro-standalone-0.0.1.jar multiple-event-types-avro/kafka/cloud.properties
```

In the Confluent Cloud Console, select the `Messages` tab for the `avro-events` topic and view the messages that are produced.

### Cleanup

Delete the cluster used for this tutorial if you no longer need it.

</details>
