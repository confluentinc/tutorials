<!-- title: How to build Kafka client applications in JavaScript with Schema Registry -->
<!-- description: In this tutorial, learn how to build Kafka client applications in JavaScript with Schema Registry, with step-by-step instructions and supporting code. -->

# How to build Kafka client applications in JavaScript with Schema Registry

This tutorial demonstrates how to build Kafka producer and consumer applications in JavaScript that use Schema Registry for message schema management. You'll learn how to configure your JavaScript applications to serialize and deserialize records, ensuring type safety and schema evolution compatibility. By the end of this tutorial, you'll have working applications that produce and consume device temperature reading records.

The applications in this tutorial use Avro-formatted messages. In order to use Protobuf or JSON Schema formatting, you would need to use a different serializer / deserializer, but otherwise the applications would be similarly structured.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the Docker instructions section at the bottom.

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- [Node.js](https://nodejs.org/en/download) version 18-24
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
  --kafka-librdkafka-properties-file ./schema-registry-js/config/cloud-kafka.properties \
  --create-sr-key \
  --schema-registry-properties-file ./schema-registry-js/config/cloud-sr.properties
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
cd schema-registry-js/src
```

The application consists of three JavaScript files:

**avro_producer.js** - This file demonstrates how to produce Avro-formatted messages to Kafka using Schema Registry:
- Defines a `TempReading` schema with `deviceId` (string) and `temperature` (float) fields
- Registers the schema with Schema Registry under the subject `readings-value`
- Creates an `AvroSerializer` configured to use the latest version of the schema
- Produces 10 temperature reading records to the `readings` topic, with random device IDs and temperatures
- Each message is serialized using the registered Avro schema before being sent to Kafka

**avro_consumer.js** - This file demonstrates how to consume Avro-formatted messages from Kafka using Schema Registry:
- Subscribes to the `readings` topic and processes each message
- Creates an `AvroDeserializer` that automatically retrieves the schema from Schema Registry based on the schema ID embedded in each message
- Deserializes message values from Avro format back into JavaScript objects and logs them to the console

**utils.js** - This file contains shared utility functions:
- `parsePropertiesFile()` - Reads a properties file into a JavaScript object
- `parseCommandLineArgs()` - Parses command-line arguments
- `loadConfigurationFromArgs()` - Loads both Kafka and Schema Registry configurations from properties files specified via command-line arguments

## Install dependencies

Install the `@confluentinc/kafka-javascript` and `@confluentinc/schemaregistry` dependencies by running the following command in the `schema-registry-js/src` directory:

```shell
npm install
```

## Run the producer application

Run the producer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

```shell
node avro_producer.js \
  --kafka-properties-file ../config/cloud-kafka.properties \
  --sr-properties-file ../config/cloud-sr.properties
```

You will see the following message as well as the logging that occurs when the producer connects and disconnects.

```plaintext
Produced 10 readings
```

## Run the consumer application

Run the consumer application, passing in the same configuration used when running the producer:

```shell
node avro_consumer.js \
  --kafka-properties-file ../config/cloud-kafka.properties \
  --sr-properties-file ../config/cloud-sr.properties
```

You will see message values like the following, along with logging when the client connects and disconnects:

```plaintext
TempReading { deviceId: '2', temperature: 78.34902954101562 }
TempReading { deviceId: '3', temperature: 91.36271667480469 }
TempReading { deviceId: '4', temperature: 73.98355865478516 }
TempReading { deviceId: '3', temperature: 54.87724685668945 }
TempReading { deviceId: '2', temperature: 83.80644989013672 }
TempReading { deviceId: '2', temperature: 52.60075378417969 }
TempReading { deviceId: '2', temperature: 95.52684783935547 }
TempReading { deviceId: '2', temperature: 75.393798828125 }
TempReading { deviceId: '1', temperature: 79.79203796386719 }
TempReading { deviceId: '4', temperature: 96.6504135131836 }
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
  - [Node.js](https://nodejs.org/en/download) version 18-24
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
  cd schema-registry-js/src
  ```

  The application consists of three JavaScript files:

  **avro_producer.js** - This file demonstrates how to produce Avro-formatted messages to Kafka using Schema Registry:
  - Defines a `TempReading` schema with `deviceId` (string) and `temperature` (float) fields
  - Registers the schema with Schema Registry under the subject `readings-value`
  - Creates an `AvroSerializer` configured to use the latest version of the schema
  - Produces 10 temperature reading records to the `readings` topic, with random device IDs and temperatures
  - Each message is serialized using the registered Avro schema before being sent to Kafka

  **avro_consumer.js** - This file demonstrates how to consume Avro-formatted messages from Kafka using Schema Registry:
  - Subscribes to the `readings` topic and processes each message
  - Creates an `AvroDeserializer` that automatically retrieves the schema from Schema Registry based on the schema ID embedded in each message
  - Deserializes message values from Avro format back into JavaScript objects and logs them to the console

  **utils.js** - This file contains shared utility functions:
  - `parsePropertiesFile()` - Reads a properties file into a JavaScript object
  - `parseCommandLineArgs()` - Parses command-line arguments
  - `loadConfigurationFromArgs()` - Loads both Kafka and Schema Registry configurations from properties files specified via command-line arguments

  ## Install dependencies

  Install the `@confluentinc/kafka-javascript` and `@confluentinc/schemaregistry` dependencies by running the following command in the `schema-registry-js/src` directory:

  ```shell
  npm install
  ```

  ## Run the producer application

  Run the producer application, passing the Kafka and Schema Registry client configuration files for connecting to Kafka and Schema Registry running in Docker:

  ```shell
  node avro_producer.js \
    --kafka-properties-file ../config/local-kafka.properties \
    --sr-properties-file ../config/local-sr.properties
  ```

  You will see the following message as well as the logging that occurs when the producer connects and disconnects.

  ```plaintext
  Produced 10 readings
  ```

  ## Run the consumer application

  Now run the consumer application, passing in the same configuration used when running the producer:

  ```shell
  node avro_consumer.js \
    --kafka-properties-file ../config/local-kafka.properties \
    --sr-properties-file ../config/local-sr.properties
  ```

  You will see message values like the following, along with logging when the client connects and disconnects:

  ```plaintext
  TempReading { deviceId: '2', temperature: 78.34902954101562 }
  TempReading { deviceId: '3', temperature: 91.36271667480469 }
  TempReading { deviceId: '4', temperature: 73.98355865478516 }
  TempReading { deviceId: '3', temperature: 54.87724685668945 }
  TempReading { deviceId: '2', temperature: 83.80644989013672 }
  TempReading { deviceId: '2', temperature: 52.60075378417969 }
  TempReading { deviceId: '2', temperature: 95.52684783935547 }
  TempReading { deviceId: '2', temperature: 75.393798828125 }
  TempReading { deviceId: '1', temperature: 79.79203796386719 }
  TempReading { deviceId: '4', temperature: 96.6504135131836 }
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```

</details>
