<!-- title: How to adopt Schema Registry in existing Apache Kafka® applications -->
<!-- description: In this tutorial, learn how to integrate Schema Registry in your existing applications without breaking legacy consumers, with step-by-step instructions and supporting code. -->

# How to adopt Schema Registry in existing Apache Kafka® applications

If you have existing Kafka applications that produce and consume messages in formats that Schema Registry supports (raw JSON, Apache Avro™, or Protobuf), you can adopt Schema Registry without breaking your legacy consumers. This tutorial demonstrates how to achieve backward compatibility by configuring producers and consumers to pass schema IDs in message headers, rather than [embedding them inline](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#wire-format-schema-id-in-the-payload-prefix) as a prefix in the message payload. This approach allows your existing consumers to continue operating normally, affording you the flexibility to upgrade consumers to use Schema Registry when convenient rather than in lockstep with producers (having to "stop the world").

Note that the applications in this tutorial are written in Python and the pre-Schema Registry applications produce and consume raw JSON messages, but the same "schema ID in headers" functionality is supported for the Java client as well as other `librdkafka`-based non-Java clients.

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
  --environment-name kafka-sr-integration-env \
  --kafka-cluster-name kafka-sr-integration-cluster \
  --create-kafka-key \
  --kafka-librdkafka-properties-file ./schema-registry-integration/cloud-kafka.properties \
  --create-sr-key \
  --schema-registry-properties-file ./schema-registry-integration/cloud-sr.properties
```

The plugin should complete in under a minute.

## Create topics

Create the topic for the application:

```shell
confluent kafka topic create users
```

## Set up Python environment

Navigate into the application's home directory:

```shell
cd schema-registry-integration/
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

## Run the non-schematized producer and consumer apps

In one terminal window, run the consumer application that deserializes plain JSON strings. Pass the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
python json_consumer.py \
  --kafka-properties-file cloud-kafka.properties
```

In another terminal window, run the corresponding producer application which serializes messages using [`json.dumps()`](https://docs.python.org/3/library/json.html#json.dumps):

```shell
python json_producer.py \
  --kafka-properties-file cloud-kafka.properties
```

Observe that the consumer simply outputs ten random user records resembling this:

```plaintext
Consumed event from topic users: key = 1b589975-a1b7-43e0-b655-46be1e278171 value = {"user_id":"1b589975-a1b7-43e0-b655-46be1e278171","name":"Blake James","email":"ashley51@example.net"}
```

## Upgrade producer

With the first version of the consumer running, let's upgrade the producer to use Schema Registry and pass schema IDs in message headers. Refer to [this tutorial](https://developer.confluent.io/confluent-tutorials/schema-registry-python/) for the basics on adding Schema Registry to your Python client applications. To pass schema IDs in message headers, there are three things to do:

- Configure the serializer with `conf={'schema.id.serializer': header_schema_id_serializer}`
- When you serialize the message value, pass in a headers object: `json_serializer(user, SerializationContext(topic, MessageField.VALUE, headers))`. The `headers` object will be populated with the schema ID GUID keyed at `__value_schema_id`.
- Pass the headers when calling `produce`:
  ```python
  producer.produce(
      topic=topic,
      key=string_serializer(user_id),
      value=serialized_value,
      headers=headers,
      on_delivery=delivery_report
  )
  ```

Run the upgraded producer application that uses Schema Registry. This application will auto-register the passed JSON Schema:

```shell
python json_schema_producer.py \
  --kafka-properties-file cloud-kafka.properties \
  --sr-properties-file cloud-sr.properties \
  --schema-file user-schema.json
```

Because the serializer is configured to use the `header_schema_id_serializer`, the message value payload will continue to be read by the original non-schematized consumer. Observe that it continues to successfully consume and output the ten additional user records just produced.

If we had instead relied on schema IDs prefixed in the message payload, then the consumer wouldn't be able to consume the messages:

```plaintext
Error occurred: 'utf-8' codec can't decode byte 0x86 in position 3: invalid start byte
```

## Upgrade consumer

Now let's upgrade the consumer application to use Schema Registry. All that's required to read the schema ID in the message header is to configure the deserializer to use the `dual_schema_id_deserializer`, which will first look for the schema ID in the message headers, and then fall back to looking for it within the message payload.

```python
deserializer_config = {
    'schema.id.deserializer': dual_schema_id_deserializer
}

json_deserializer = JSONDeserializer(schema_registry_client=schema_registry_client,
                                     schema_str=schema_str,
                                     conf=deserializer_config,
                                     from_dict=User.dict_to_user)
```

To run the upgraded consumer, first stop the original consumer if it's still running. Then run the upgraded consumer:

```shell
python json_schema_consumer.py \
  --kafka-properties-file cloud-kafka.properties \
  --sr-properties-file cloud-sr.properties \
  --schema-file user-schema.json
```

Now in another terminal window run the upgraded producer once more to ensure that the upgraded consumer is working as expected:

```shell
python json_schema_producer.py \
  --kafka-properties-file cloud-kafka.properties \
  --sr-properties-file cloud-sr.properties \
  --schema-file user-schema.json
```

You will see that, because the upgraded consumer uses the same consumer group ID, the upgraded consumer outputs only the ten new records just produced:

```plaintext
Consumed user: user_id = 1f9f5b43-35a0-4fee-96ec-d62d14207b1f, name = Donna Scott, email = tparker@example.com

...
```

## Clean up

When you're finished, delete the `kafka-sr-integration-env` environment. First, get its environment ID (of the form `env-123456`):

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```
