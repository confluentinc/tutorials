<!-- title: How to build Kafka client applications in C# with Schema Registry -->
<!-- description: In this tutorial, learn how to build Kafka client applications in C# with Schema Registry, with step-by-step instructions and supporting code. -->

# How to build Kafka client applications in C# with Schema Registry

This tutorial demonstrates how to build Kafka producer and consumer applications in C# that use Schema Registry for message schema management. You'll learn how to configure your .NET applications to serialize and deserialize records, ensuring type safety and schema evolution compatibility. By the end of this tutorial, you'll have working applications that produce and consume device temperature reading records.

The applications in this tutorial use Avro-formatted messages. To use Protobuf or JSON Schema formatting, you would need to use a different serializer / deserializer ([Protobuf](https://docs.confluent.io/platform/current/clients/confluent-kafka-dotnet/_site/api/Confluent.SchemaRegistry.Serdes.ProtobufSerializer-1.html), [JSON Schema](https://docs.confluent.io/platform/current/clients/confluent-kafka-dotnet/_site/api/Confluent.SchemaRegistry.Serdes.JsonSerializer-1.html)), but otherwise the applications would be similarly structured.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the Docker instructions section at the bottom.

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- [.NET Core](https://dotnet.microsoft.com/download) version 8.0 or later
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
  --kafka-librdkafka-properties-file ./schema-registry-dotnet/config/cloud-kafka.properties \
  --create-sr-key \
  --schema-registry-properties-file ./schema-registry-dotnet/config/cloud-sr.properties
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
cd schema-registry-dotnet/src
```

There are three projects included in the example:

### `AvroProducer`

**Dependencies** (`AvroProducer.csproj`):
- `Confluent.Kafka`: Kafka client library
- `Confluent.SchemaRegistry`: Schema Registry client
- `Confluent.SchemaRegistry.Serdes.Avro`: Avro serialization support

`AvroProducer.cs` demonstrates how to produce Avro-serialized messages to Kafka with Schema Registry integration:

1. **Configuration**: Loads Kafka producer and Schema Registry configuration from properties files using utility methods from the `Common` project.
2. **Schema Registry Client**: Creates a `CachedSchemaRegistryClient` that caches schemas for efficient lookups and communicates with Schema Registry.
3. **Producer Setup**: Builds a Kafka producer with string keys and `TempReading` values, configuring an `AvroSerializer` that automatically registers the schema with Schema Registry on first use and serializes messages to Avro format.
4. **Message Production**: Generates random device ID / temperature reading pairs and produces each message to the `readings` topic.

### `AvroConsumer`

**Dependencies** (`AvroConsumer.csproj`):
- `Confluent.Kafka`: Kafka client library
- `Confluent.SchemaRegistry`: Schema Registry client
- `Confluent.SchemaRegistry.Serdes.Avro`: Avro deserialization support

`AvroConsumer.cs` demonstrates how to consume Avro-serialized messages from Kafka with Schema Registry integration:

1. **Configuration**: Loads Kafka consumer and Schema Registry configuration from properties files, just as the producer app does.
2. **Schema Registry Client**: Creates a `CachedSchemaRegistryClient` for efficient schema lookups.
3. **Consumer Setup**: Builds a Kafka consumer with string keys and Avro specific `TempReading` values, configuring an `AvroDeserializer` that automatically retrieves the schema from Schema Registry and deserializes Avro formatted messages.
4. **Subscription**: Subscribes to the `readings` topic.
5. **Consumer Loop**: Continuously polls for messages, deserializes them into strongly-typed `TempReading` objects, and prints the device ID and temperature values to the console.

### `Common`

The `Common` project is a shared library containing utilities and models used by both the producer and consumer applications. It includes configuration loaders (`Properties.cs`), command-line parsing (`CommandLineOptions.cs`), and the auto-generated Avro class `TempReading` representing temperature readings with `deviceId` and `temperature` fields.

## Compile the applications

Build the producer and consumer applications by running the following command from the `schema-registry-dotnet` directory:

```shell
dotnet build schema-registry-dotnet.sln
```

## Run the producer application

Run the producer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

```shell
dotnet run \
    --project src/AvroProducer/AvroProducer.csproj -- \
    --kafka-properties-file config/cloud-kafka.properties \
    --sr-properties-file config/cloud-sr.properties
```

You will see that ten readings produced to Kafka are logged to the console like this:

```plaintext
10 messages produced to topic readings
```

## Run the consumer application

Run the consumer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

```shell
dotnet run \
    --project src/AvroConsumer/AvroConsumer.csproj -- \
    --kafka-properties-file config/cloud-kafka.properties \
    --sr-properties-file config/cloud-sr.properties
```

You will see output like the following:

```plaintext
Consumed reading deviceId: 1, temperature: 66.65975
Consumed reading deviceId: 3, temperature: 57.096996
Consumed reading deviceId: 3, temperature: 51.907024
Consumed reading deviceId: 2, temperature: 87.31283
Consumed reading deviceId: 3, temperature: 67.84723
Consumed reading deviceId: 2, temperature: 71.68407
Consumed reading deviceId: 3, temperature: 77.41191
Consumed reading deviceId: 2, temperature: 78.22028
Consumed reading deviceId: 3, temperature: 78.91205
Consumed reading deviceId: 3, temperature: 58.753788
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
  - [.NET Core](https://dotnet.microsoft.com/download) version 8.0 or later
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
  cd schema-registry-dotnet/src
  ```

  There are three projects included in the example:

  ### `AvroProducer`

  **Dependencies** (`AvroConsumer.csproj`):
  - `Confluent.Kafka`: Kafka client library
  - `Confluent.SchemaRegistry`: Schema Registry client
  - `Confluent.SchemaRegistry.Serdes.Avro`: Avro serialization support

  `AvroProducer.cs` demonstrates how to produce Avro-serialized messages to Kafka with Schema Registry integration:

  1. **Configuration**: Loads Kafka producer and Schema Registry configuration from properties files using utility methods from the `Common` project.
  2. **Schema Registry Client**: Creates a `CachedSchemaRegistryClient` that caches schemas for efficient lookups and communicates with Schema Registry.
  3. **Producer Setup**: Builds a Kafka producer with string keys and `TempReading` values, configuring an `AvroSerializer` that automatically registers the schema with Schema Registry on first use and serializes messages to Avro format.
  4. **Message Production**: Generates random device ID / temperature reading pairs and produces each message to the `readings` topic.

  ### `AvroConsumer`

  **Dependencies** (`AvroConsumer.csproj`):
  - `Confluent.Kafka`: Kafka client library
  - `Confluent.SchemaRegistry`: Schema Registry client
  - `Confluent.SchemaRegistry.Serdes.Avro`: Avro deserialization support

  `AvroConsumer.cs` demonstrates how to consume Avro-serialized messages from Kafka with Schema Registry integration:

  1. **Configuration**: Loads Kafka consumer and Schema Registry configuration from properties files, just as the producer app does.
  2. **Schema Registry Client**: Creates a `CachedSchemaRegistryClient` for efficient schema lookups.
  3. **Consumer Setup**: Builds a Kafka consumer with string keys and Avro specific `TempReading` values, configuring an `AvroDeserializer` that automatically retrieves the schema from Schema Registry and deserializes Avro formatted messages.
  4. **Subscription**: Subscribes to the `readings` topic.
  5. **Consumer Loop**: Continuously polls for messages, deserializes them into strongly-typed `TempReading` objects, and prints the device ID and temperature values to the console.

  ### `Common`

  The `Common` project is a shared library containing utilities and models used by both the producer and consumer applications. It includes configuration loaders (`Properties.cs`), command-line parsing (`CommandLineOptions.cs`), and the auto-generated Avro class `TempReading` representing temperature readings with `deviceId` and `temperature` fields.

  ## Compile the applications

  Build the producer and consumer applications by running the following command from the `schema-registry-dotnet` directory:

  ```shell
  dotnet build schema-registry-dotnet.sln
  ```

  ## Run the producer application

  Run the producer application, passing the client configuration files pointing to Kafka and Schema Registry running in Docker.

  ```shell
  dotnet run \
      --project src/AvroProducer/AvroProducer.csproj -- \
      --kafka-properties-file config/local-kafka.properties \
      --sr-properties-file config/local-sr.properties
  ```

  You will see that ten readings produced to Kafka are logged to the console like this:

  ```plaintext
  10 messages produced to topic readings
  ```

  ## Run the consumer application

  Now run the consumer application:

  ```shell
  dotnet run \
      --project src/AvroConsumer/AvroConsumer.csproj -- \
      --kafka-properties-file config/local-kafka.properties \
      --sr-properties-file config/local-sr.properties
  ```

  You will see output like the following:

  ```plaintext
  Consumed reading deviceId: 3, temperature: 81.7913
  Consumed reading deviceId: 4, temperature: 79.99652
  Consumed reading deviceId: 4, temperature: 64.110596
  Consumed reading deviceId: 4, temperature: 89.80548
  Consumed reading deviceId: 1, temperature: 67.72774
  Consumed reading deviceId: 2, temperature: 88.04513
  Consumed reading deviceId: 1, temperature: 98.9442
  Consumed reading deviceId: 3, temperature: 57.281647
  Consumed reading deviceId: 1, temperature: 56.34163
  Consumed reading deviceId: 3, temperature: 54.051064
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```

</details>
