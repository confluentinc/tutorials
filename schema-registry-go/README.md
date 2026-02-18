<!-- title: How to build Kafka client applications in Go with Schema Registry -->
<!-- description: In this tutorial, learn how to build Kafka client applications in Go with Schema Registry, with step-by-step instructions and supporting code. -->


# How to build Kafka client applications in Go with Schema Registry

This tutorial demonstrates how to build Kafka producer and consumer applications in Go that use Schema Registry for message schema management. You'll learn how to configure your Go applications to serialize and deserialize records, ensuring type safety and schema evolution compatibility. By the end of this tutorial, you'll have working applications that produce and consume device temperature reading records.

The applications in this tutorial use Avro-formatted messages. In order to use Protobuf or JSON Schema formatting, you would need to use a different serializer / deserializer ([Protobuf](https://pkg.go.dev/github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/protobuf), [JSON Schema](https://pkg.go.dev/github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/jsonschema)), but otherwise the applications would be similarly structured.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the Docker instructions section at the bottom.

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- [Go](https://go.dev/) version 1.24 or later
- Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Create Confluent Cloud resources

Log in to your Confluent Cloud account:

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
  --environment-name kafka-sr-env \
  --kafka-cluster-name kafka-sr-cluster \
  --create-kafka-key \
  --kafka-librdkafka-properties-file ./schema-registry-go/config/cloud-kafka.properties \
  --create-sr-key \
  --schema-registry-properties-file ./schema-registry-go/config/cloud-sr.properties
```

The plugin should complete in under a minute.

## Create topics

Create the topic for the application:

```shell
confluent kafka topic create readings
```

## Review the application source code

Navigate into the application's source code directory:

```shell
cd schema-registry-go/src
```

This folder contains the following source files:

`avro_producer.go`: Implements a Kafka producer that generates random Avro-formatted temperature readings and produces them to the `readings` topic.

`avro_consumer.go`: Implements a Kafka consumer that subscribes to the `readings` topic and continuously polls for new messages, deserializing them from Avro format and logging them to the console.

`temp_reading.go`: Defines the `TempReading` domain object struct with `deviceId` (string) and `temperature` (float32) fields, tagged for Avro serialization.

`utils.go`: Contains shared utility functions used by both producer and consumer: `ParseConfigFlags()` for parsing command-line flags, `LoadKafkaConfig()` for loading Kafka configuration from properties files, and `CreateSchemaRegistryClient()` for creating a Schema Registry client with optional basic authentication.

## Compile and run the producer application

Compile the producer application from the `schema-registry-go` folder:

```shell
go build -o out/avro_producer \
  src/temp_reading.go \
  src/utils.go \
  src/avro_producer.go
```

Run the producer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

```shell
./out/avro_producer \
  --kafka-properties-file ./config/cloud-kafka.properties \
  --sr-properties-file ./config/cloud-sr.properties
```

You will see that ten readings produced to Kafka are logged to the console like this:

```plaintext
Delivered message to topic readings [0] at offset 0: DeviceId=4, Temperature=71.06
```

## Compile and run the consumer application

Compile the consumer application:

```shell
go build -o out/avro_consumer \
  src/temp_reading.go \
  src/utils.go \
  src/avro_consumer.go
```

Run the consumer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

```shell
./out/avro_consumer \
  --kafka-properties-file ./config/cloud-kafka.properties \
  --sr-properties-file ./config/cloud-sr.properties
```

You will see output like the following prepended with timestamps:

```plaintext
{DeviceID:1 Temperature:62.20346}
{DeviceID:2 Temperature:88.26609}
{DeviceID:4 Temperature:51.299347}
{DeviceID:3 Temperature:99.0374}
{DeviceID:3 Temperature:90.82821}
{DeviceID:2 Temperature:54.175503}
{DeviceID:2 Temperature:67.71871}
{DeviceID:2 Temperature:73.04611}
{DeviceID:1 Temperature:98.31503}
{DeviceID:1 Temperature:65.337845}
```

## Clean up

When you're finished, delete the `kafka-sr-env` environment. First, get its environment ID (of the form `env-123456`):

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

  - Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  - [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  - [Go](https://go.dev/) version 1.17 or later
  - Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

  ## Start Kafka and Schema Registry in Docker

  Start Kafka and Schema Registry with the following command from the top-level `tutorials` repository:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml up -d
  ```

  ## Create topics

  Open a shell in the broker container:

  ```shell
  docker exec -it broker /bin/bash
  ```

  Create the topic for the application:

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic readings
  ```

  ## Review the application source code

  Navigate into the application's source code directory:

  ```shell
  cd schema-registry-go/src
  ```

  This folder contains the following source files:

  `avro_producer.go`: Implements a Kafka producer that generates random Avro-formatted temperature readings and produces them to the `readings` topic.

  `avro_consumer.go`: Implements a Kafka consumer that subscribes to the `readings` topic and continuously polls for new messages, deserializing them from Avro format and logging them to the console.

  `temp_reading.go`: Defines the `TempReading` domain object struct with `deviceId` (string) and `temperature` (float32) fields, tagged for Avro serialization.

  `utils.go`: Contains shared utility functions used by both producer and consumer: `ParseConfigFlags()` for parsing command-line flags, `LoadKafkaConfig()` for loading Kafka configuration from properties files, and `CreateSchemaRegistryClient()` for creating a Schema Registry client with optional basic authentication.

  ## Compile and run the producer application

  Compile the producer application from the `schema-registry-go` folder:

  ```shell
  go build -o out/avro_producer \
    src/temp_reading.go \
    src/utils.go \
    src/avro_producer.go
  ```

  Run the producer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

  ```shell
  ./out/avro_producer \
    --kafka-properties-file ./config/local-kafka.properties \
    --sr-properties-file ./config/local-sr.properties
  ```

  You will see that ten readings produced to Kafka are logged to the console like this:

  ```plaintext
  Delivered message to topic readings [0] at offset 0: DeviceId=4, Temperature=71.06
  ```

  ## Compile and run the consumer application

  Compile the consumer application:

  ```shell
  go build -o out/avro_consumer \
    src/temp_reading.go \
    src/utils.go \
    src/avro_consumer.go
  ```

  Run the consumer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

  ```shell
  ./out/avro_consumer \
    --kafka-properties-file ./config/local-kafka.properties \
    --sr-properties-file ./config/local-sr.properties
  ```

  You will see output like the following prepended with timestamps:

  ```plaintext
  {DeviceID:1 Temperature:62.20346}
  {DeviceID:2 Temperature:88.26609}
  {DeviceID:4 Temperature:51.299347}
  {DeviceID:3 Temperature:99.0374}
  {DeviceID:3 Temperature:90.82821}
  {DeviceID:2 Temperature:54.175503}
  {DeviceID:2 Temperature:67.71871}
  {DeviceID:2 Temperature:73.04611}
  {DeviceID:1 Temperature:98.31503}
  {DeviceID:1 Temperature:65.337845}
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```

</details>
