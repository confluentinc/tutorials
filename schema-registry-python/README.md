<!-- title: How to build Kafka client applications in Python with Schema Registry -->
<!-- description: In this tutorial, learn how to build Kafka client applications in Python with Schema Registry, with step-by-step instructions and supporting code. -->

# How to build Kafka client applications in Python with Schema Registry

This tutorial demonstrates how to build Kafka producer and consumer applications in Python that use Schema Registry for message schema management. You'll learn how to configure your Python applications to serialize and deserialize records, ensuring type safety and schema evolution compatibility. By the end of this tutorial, you'll have working applications that produce and consume device temperature reading records.

The applications in this tutorial use Avro-formatted messages. In order to use Protobuf or JSON Schema formatting, you would need to use a [different serializer / deserializer](https://docs.confluent.io/platform/current/clients/confluent-kafka-python/html/index.html#serialization-api), but otherwise the applications would be similarly structured.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the Docker instructions section at the bottom.

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- [Python](https://www.python.org/) version 3.11 or later
- [virtualenv](https://virtualenv.pypa.io/en/latest/installation.html), or your preferred Python virtual environment tool
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
  --kafka-librdkafka-properties-file ./schema-registry-python/cloud-kafka.properties \
  --create-sr-key \
  --schema-registry-properties-file ./schema-registry-python/cloud-sr.properties
```

The plugin should complete in under a minute.

## Create topics

Create the topic for the application:

```shell
confluent kafka topic create readings
```

## Set up Python environment

Navigate into the application's home directory:

```shell
cd schema-registry-python/
```

Create and activate a Python virtual environment to give yourself an isolated workspace for installing dependencies. To use `virtualenv`:

```shell
virtualenv env
source env/bin/activate
```

Install the application's dependencies:

```shell
pip install -r requirements.txt
```

This installs the following dependencies:

- [`confluent-kafka`](https://docs.confluent.io/kafka-clients/python/current/overview.html): Confluent's Python client for Apache Kafka®
- [`jproperties`](https://pypi.org/project/jproperties/): a library for parsing a properties file

## Review the application source code

`avro_producer.py`: Implements a Kafka producer that generates 10 random temperature readings and publishes them to the `readings` topic. The application loads Kafka and Schema Registry configuration from properties files, creates an Avro serializer configured with Schema Registry, and produces messages with device IDs as keys and serialized `TempReading` objects as values.

`avro_consumer.py`: Implements a Kafka consumer that subscribes to the `readings` topic and continuously polls for new messages. The application uses an Avro deserializer configured with Schema Registry to convert incoming message bytes back into `TempReading` domain objects, which are then printed to the console.

`temp-reading.avsc`: Defines the Avro schema for temperature reading records with two fields: a string `device_id`  and a float `temperature`.

`temp_reading.py`: Contains the `TempReading` class that represents a temperature reading domain object. The class also includes two static helper methods: `reading_to_dict` for serialization and `dict_to_reading` for deserialization, which are used by the Avro serializer and deserializer.

## Run the producer application

Run the producer application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

```shell
python avro_producer.py \
  --kafka-properties-file cloud-kafka.properties \
  --sr-properties-file cloud-sr.properties \
  --schema-file temp-reading.avsc
```

## Run the consumer application

In a new terminal window, run the consumer application:

```shell
python avro_consumer.py \
  --kafka-properties-file cloud-kafka.properties \
  --sr-properties-file cloud-sr.properties \
  --schema-file temp-reading.avsc
```

You will see output like:

```plaintext
Consumed reading: device_id = 4, temperature = 100.78572082519531
Consumed reading: device_id = 2, temperature = 93.64944458007812
Consumed reading: device_id = 1, temperature = 85.31315612792969
Consumed reading: device_id = 4, temperature = 79.18598175048828
Consumed reading: device_id = 3, temperature = 93.05386352539062
Consumed reading: device_id = 3, temperature = 106.12528991699219
Consumed reading: device_id = 3, temperature = 103.54008483886719
Consumed reading: device_id = 4, temperature = 79.39240264892578
Consumed reading: device_id = 3, temperature = 95.86831665039062
Consumed reading: device_id = 3, temperature = 106.3673095703125
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
  - [Python](https://www.python.org/) version 3.11 or later
  - [virtualenv](https://virtualenv.pypa.io/en/latest/installation.html), or your preferred Python virtual environment tool
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

  ## Set up Python environment

  Navigate into the application's home directory:

  ```shell
  cd schema-registry-python/
  ```

  Create and activate a Python virtual environment to give yourself an isolated workspace for installing dependencies. To use `virtualenv`:

  ```shell
  virtualenv env
  source env/bin/activate
  ```

  Install the application's dependencies:

  ```shell
  pip install -r requirements.txt
  ```

  This installs the following dependencies:

  - [`confluent-kafka`](https://docs.confluent.io/kafka-clients/python/current/overview.html): Confluent's Python client for Apache Kafka®
  - [`jproperties`](https://pypi.org/project/jproperties/): a library for parsing a properties file

  ## Review the application source code

  `avro_producer.py`: Implements a Kafka producer that generates 10 random temperature readings and publishes them to the `readings` topic. The application loads Kafka and Schema Registry configuration from properties files, creates an Avro serializer configured with Schema Registry, and produces messages with device IDs as keys and serialized TempReading objects as values.

  `avro_consumer.py`: Implements a Kafka consumer that subscribes to the `readings` topic and continuously polls for new messages. The application uses an Avro deserializer configured with Schema Registry to convert incoming message bytes back into `TempReading` domain objects, which are then printed to the console.

  `temp-reading.avsc`: Defines the Avro schema for temperature reading records with two fields: a string `device_id`  and a float `temperature`.

  `temp_reading.py`: Contains the `TempReading` class that represents a temperature reading domain object. The class also includes two static helper methods: `reading_to_dict` for serialization and `dict_to_reading` for deserialization, which are used by the Avro serializer and deserializer.

  ## Run the producer application

  Run the application, passing the Kafka and Schema Registry client configuration files for connecting to Kafka and Schema Registry running in Docker:

  ```shell
  python avro_producer.py \
    --kafka-properties-file local-kafka.properties \
    --sr-properties-file local-sr.properties \
    --schema-file temp-reading.avsc
  ```

  ## Run the consumer application

  In a new terminal window, run the consumer application:

  ```shell
  python avro_consumer.py \
    --kafka-properties-file local-kafka.properties \
    --sr-properties-file local-sr.properties \
    --schema-file temp-reading.avsc
  ```

  You will see output like:

  ```plaintext
  Consumed reading: device_id = 4, temperature = 100.78572082519531
  Consumed reading: device_id = 2, temperature = 93.64944458007812
  Consumed reading: device_id = 1, temperature = 85.31315612792969
  Consumed reading: device_id = 4, temperature = 79.18598175048828
  Consumed reading: device_id = 3, temperature = 93.05386352539062
  Consumed reading: device_id = 3, temperature = 106.12528991699219
  Consumed reading: device_id = 3, temperature = 103.54008483886719
  Consumed reading: device_id = 4, temperature = 79.39240264892578
  Consumed reading: device_id = 3, temperature = 95.86831665039062
  Consumed reading: device_id = 3, temperature = 106.3673095703125
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```

</details>
