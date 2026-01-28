<!-- title: How to produce messages to an Apache Kafka&reg; topic using the Python asyncio client -->
<!-- description: In this tutorial, learn how to produce messages to an Apache Kafka&reg; topic using the Python asyncio client, with step-by-step instructions and supporting code. -->

# How to produce messages to an Apache Kafka&reg; topic using the Python asyncio client

This tutorial walks you through producing messages to Kafka via a Python web service using the Confluent Python Client for Apache Kafka®. It focuses on the library's asynchronous Kafka API in order to showcase how to interact with Kafka in [`asyncio`](https://docs.python.org/3/library/asyncio.html)-based applications. It uses Quart given that it also [natively supports](https://quart.palletsprojects.com/en/latest/tutorials/asyncio/) `asyncio`.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the `Docker instructions` section at the bottom.

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
  --environment-name kafka-python-async-env \
  --kafka-cluster-name kafka-python-async-cluster \
  --create-kafka-key \
  --kafka-librdkafka-properties-file ./kafka-python-async/cloud-kafka.properties \
  --create-sr-key \
  --schema-registry-properties-file ./kafka-python-async/cloud-sr.properties
```

The plugin should complete in under a minute.

## Create topics

Create the topics for the application: a `readings` topic for temperature readings without an associated schema, and `readings_schematized` which will have an associated schema.

```shell
confluent kafka topic create readings
confluent kafka topic create readings_schematized
```

## Set up Python environment

Navigate into the application's home directory:

```shell
cd kafka-python-async/
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
- [`quart`](https://quart.palletsprojects.com/en/stable/index.html): a framework for web serving (similar to [Flask](https://flask.palletsprojects.com/en/stable/)). The application in this tutorial is a scalable Quart application whose APIs produce events to Kafka.

## Review the application source code

The application source code lives in `app.py`. Open this file in a text editor or IDE.

The first two functions, `create_app` and `cli_wrapper`, define the Quart application and handle command-line parsing. The application takes as input files containing Kafka and Schema Registry endpoints and credentials (if applicable) so that the same source code can be used against any Kafka environment.

The next three functions are Quart application lifecycle coroutines that create a Kafka producer client and a Schema Registry client before the application begins serving requests (dictated by the `@app.before_serving` Quart decorator), and delete them when the application exits (dictated by the `@app.after_serving` decorator).

To create Kafka and Schema Registry clients:

```python
producer = AIOProducer(producer_conf.properties)
schema_registry_client = AsyncSchemaRegistryClient(schema_registry_conf.properties)
```

To close the clients, only the producer needs to be flushed (optionally) and closed via coroutines that you `await`:

```python
await producer.flush()
await producer.close()
```

Finally, the `record_temp` coroutine defines an HTTP API for posting temperature readings. The Kafka API to produce events asynchronously is straightforward: call `produce`, which returns a `Future`; then await the `Future` to get the delivered `Message` status, including any errors.

```python
@app.post("/record_temp/<temp>")
async def record_temp(temp):
    try:
        delivery_future = await producer.produce("readings", value=temp)
        delivered_msg = await delivery_future
        if delivered_msg.error() is not None:
            return jsonify({"status": "error", "message": f"{delivered_msg.error().str()}"}), 500
        else:
            return jsonify({"status": "success"}), 200
    except KafkaException as ke:
        return jsonify({"status": "error", "message": f"{ke.args[0].str()}"}), 500
    except:
        return jsonify({"status": "error", "message": "Unknown Error"}), 500
```

The `record_temp_schematized` coroutine looks very similar, with the main difference being that we first serialize the value with the `AsyncAvroSerializer` instantiated during application initialization:

```python
value = {"temp": temp}
serialized_value = await avro_serializer(value,
                                         SerializationContext("readings_schematized",
                                                              MessageField.VALUE))
delivery_future = await producer.produce("readings_schematized", value=serialized_value)
delivered_msg = await delivery_future
```

## Run the application

Run the application, passing the Kafka and Schema Registry client configuration files generated when you created Confluent Cloud resources:

```shell
quart serve ./cloud-kafka.properties ./cloud-sr.properties
```

## Produce raw events

In a new terminal window, start a console consumer:

```shell
confluent kafka topic consume readings
```

Next, send events to the `/record_temp/` route, which will, in turn, produce temperature readings to the `readings` topic:

```shell
curl -X POST localhost:5000/record_temp/78.2
```

You will see a success response:

```plaintext
{"status":"success"}
```

If you pass a `-I` option to curl you would see an HTTP 200 response code indicating success.

Feel free to send more readings and verify that they show up in the console consumer.

## Produce schematized events

Producing events that have an associated schema works similarly. First, start a console consumer configured to interpret Avro-formatted message values:

```shell
confluent kafka topic consume readings_schematized \
    --value-format avro \
    --from-beginning
```

Next, send events to the `/record_temp_schematized/` route, which will, in turn, produce temperature readings to the `readings_schematized` topic:

```shell
curl -X POST localhost:5000/record_temp_schematized/92.31
```

You will see a success response:

```plaintext
{"status":"success"}
```

In the console consumer, you'll see the schematized reading:

```plaintext
{"temp":92.31}
```

## Clean up

When you're finished, delete the `kafka-python-async-env` environment. First, get its environment ID (of the form `env-123456`):

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

  Create the topics for the application: a `readings` topic for temperature readings without an associated schema, and `readings_schematized` which will have an associated schema.

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic readings
  kafka-topics --bootstrap-server localhost:9092 --create --topic readings_schematized
  ```

  ## Set up Python environment

  Navigate into the application's home directory:

  ```shell
  cd kafka-python-async/
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
  - [`quart`](https://quart.palletsprojects.com/en/stable/index.html): a framework for web serving (similar to [Flask](https://flask.palletsprojects.com/en/stable/)). The application in this tutorial is a scalable Quart application whose APIs produce events to Kafka.

  ## Review the application source code

  The application source code lives in `app.py`. Open this file in a text editor or IDE.

  The first two functions, `create_app` and `cli_wrapper`, define the Quart application and handle command-line parsing. The application takes as input files containing Kafka and Schema Registry endpoints and credentials (if applicable) so that the same source code can be used against any Kafka environment.

  The next three functions are Quart application lifecycle coroutines that create a Kafka producer client and a Schema Registry client before the application begins serving requests (dictated by the `@app.before_serving` Quart decorator), and delete them when the application exits (dictated by the `@app.after_serving` decorator).

  To create Kafka and Schema Registry clients:

  ```python
  producer = AIOProducer(producer_conf.properties)
  schema_registry_client = AsyncSchemaRegistryClient(schema_registry_conf.properties)
  ```

  To close the clients, only the producer needs to be flushed (optionally) and closed via coroutines that you `await`:

  ```python
  await producer.flush()
  await producer.close()
  ```

  Finally, the `record_temp` coroutine defines an HTTP API for posting temperature readings. The Kafka API to produce events asynchronously is straightforward: call `produce`, which returns a `Future`; then await the `Future` to get the delivered `Message` status, including any errors.

  ```python
  @app.post("/record_temp/<temp>")
  async def record_temp(temp):
    try:
      delivery_future = await producer.produce("readings", value=temp)
      delivered_msg = await delivery_future
      if delivered_msg.error() is not None:
        return jsonify({"status": "error", "message": f"{delivered_msg.error().str()}"}), 500
      else:
        return jsonify({"status": "success"}), 200
    except KafkaException as ke:
      return jsonify({"status": "error", "message": f"{ke.args[0].str()}"}), 500
    except:
      return jsonify({"status": "error", "message": "Unknown Error"}), 500
  ```

  The `record_temp_schematized` coroutine looks very similar, with the main difference being that we first serialize the value with the `AsyncAvroSerializer` instantiated during application initialization:

  ```python
  value = {"temp": temp}
  serialized_value = await avro_serializer(value, 
                                           SerializationContext("readings_schematized", 
                                                                MessageField.VALUE))
  delivery_future = await producer.produce("readings_schematized", value=serialized_value)
  delivered_msg = await delivery_future
  ```

  ## Run the application

  Run the application, passing the Kafka and Schema Registry client configuration files for connecting to Kafka and Schema Registry running in Docker:

  ```shell
  quart serve ./local-kafka.properties ./local-sr.properties
  ```

  ## Produce raw events

  In a new terminal window, open a shell in the broker container and start a console consumer:

  ```shell
  docker exec -it broker /bin/bash
  ```

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic readings --from-beginning
  ```

  Next, send events to the `/record_temp/` route, which will, in turn, produce temperature readings to the `readings` topic:

  ```shell
  curl -X POST localhost:5000/record_temp/78.2
  ```

  You will see a success response:

  ```plaintext
  {"status":"success"}
  ```

  If you pass a `-I` option to curl you would see an HTTP 200 response code indicating success.

  Feel free to send more readings and verify that they show up in the console consumer.

  ## Produce schematized events

  Producing events that have an associated schema works similarly. First, start a console consumer for Avro-formatted message values by opening a shell in the Schema Registry container and running the included `kafka-avro-console-consumer` command-line utility:

  ```shell
  docker exec -it schema-registry /bin/bash
  ```

  ```shell
  kafka-avro-console-consumer \
    --topic readings_schematized \
    --bootstrap-server broker:29092 \
    --property schema.registry.url=http://localhost:8081 \
    --from-beginning
  ```

  Next, send events to the `/record_temp_schematized/` route, which will, in turn, produce temperature readings to the `readings_schematized` topic:

  ```shell
  curl -X POST localhost:5000/record_temp_schematized/92.31
  ```

  You will see a success response:

  ```plaintext
  {"status":"success"}
  ```

  In the console consumer, you'll see the schematized reading:

  ```plaintext
  {"temp":92.31}
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```

</details>
