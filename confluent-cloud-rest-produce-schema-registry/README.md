<!-- title: How to produce Avro-formatted Kafka messages to Confluent Cloud via REST API -->
<!-- description: In this tutorial, learn how to produce Avro-formatted Kafka messages to Confluent Cloud via REST API, with step-by-step instructions and supporting code. -->

# How to produce Avro-formatted Kafka messages to Confluent Cloud via REST API

Confluent Cloud provides a set of [REST APIs](https://docs.confluent.io/cloud/current/api.html) for interacting with cloud resources. The [Produce API](https://docs.confluent.io/cloud/current/kafka-rest/kafka-rest-cc.html#produce-api) allows clients to produce records to a Kafka topic via an HTTP POST request. However, this API currently does not support a built-in way to produce records that use schemas from Schema Registry. Despite this limitation, clients can still produce such records by sending binary data that conforms to the message wire format documented [here](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#wire-format). This tutorial walks through the necessary steps.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* A Kafka cluster in Confluent Cloud (e.g., following this [quick start step](https://docs.confluent.io/cloud/current/get-started/index.html#section-1-create-a-cluster-and-add-a-topic))
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* [`jq`](https://jqlang.org/download/) for parsing JSON on the command line
* Clone the `confluentinc/tutorials` GitHub repository and navigate to the appropriate directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials/confluent-cloud-rest-produce-schema-registry
  ```

## Set active cluster in Confluent CLI

Log in to Confluent Cloud using the Confluent CLI:

```shell
confluent login --prompt
```

Next, set the active environment and Kafka cluster to make running later commands easier.

Find your environment ID of the form `env-abcdef`:

```shell
confluent environment list
```

Set it as the active environment:

```shell
confluent environment use <ENVIRONMENT ID>
```

Similarly, find your Kafka cluster ID of the form `lkc-abcdef`:

```shell
confluent kafka cluster list
```

Set it as the active cluster:

```shell
confluent kafka cluster use <CLUSTER ID>
```

## Create a topic and associated schema

Create a Kafka topic named `orders`:

```shell
confluent kafka topic create orders
```

Associate the schema in the file `order.avsc` with the record values in the `orders` topic:

```shell
confluent schema-registry schema create --subject orders-value --schema order.avsc --type avro
```

The schema defines an order with a customer ID, product ID, and product name:

```json
{
  "type": "record",
  "namespace": "io.confluent.developer",
  "name": "Order",
  "fields": [
    {
      "name": "product_id",
      "type": "int"
    },
    {
      "name": "customer_id",
      "type": "int"
    },
    {
      "name": "product_name",
      "type": "string"
    }
  ]
}
```

## Generate base64-encoded message

Following the Produce API [data payload specification](https://docs.confluent.io/cloud/current/kafka-rest/kafka-rest-cc.html#data-payload-specification), we must send base64-encoded binary data that conforms to this [wire format](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#wire-format).

The GitHub repo contains a sample Python script to help with this.

First, instantiate a byte buffer and write the magic byte (0), followed by the schema ID in the next 4 bytes:

```python
buffer = io.BytesIO()

# write magic byte (zero)
buffer.write(b'\x00')

# write schema ID in next 4 bytes
buffer.write(args.schema_id.to_bytes(4))
```

Next, write the Avro-formatted data from `payload.json` into the same buffer. The sample payload looks like:

```json
{
  "product_id": 19287,
  "customer_id": 8737,
  "product_name": "AirSwift Light Sneakers"
}
```

To write this binary-encoded data using the [Avro Python API](https://avro.apache.org/docs/++version++/api/py/html/):

```python
# write the Avro-formatted data
DatumWriter(schema).write(record, BinaryEncoder(buffer))
```

Finally, base64-encode the entire payload and print it:

```python
print(base64.b64encode(buffer.getvalue()).decode("utf-8"))
```

To run the script and generate the encoded payload:

1. Get the schema ID:
   ```shell
   confluent schema-registry schema describe  --subject orders-value --version latest | grep "Schema ID"
   ```
2. Install the `avro` package and run the script, passing the schema ID from the previous step:
   ```shell
   pip install avro
   PAYLOAD=$(./generate_sample_avro_value.py --schema-path ./order.avsc \
                                             --payload-path ./payload.json \
                                             --schema-id <SCHEMA ID>)
   ```
   This prints the payload that we will post to the Produce REST API:
   ```noformat
   $ echo $PAYLOAD
   AAABhqOurQLCiAEuQWlyU3dpZnQgTGlnaHQgU25lYWtlcnM=
   ```

## Send message to Produce API

With the base64-encoded message ready, set the environment variables `REST_ENDPOINT` and `CLUSTER_ID` by describing the cluster:

```shell
read -r CLUSTER_ID REST_ENDPOINT < \
    <(confluent kafka cluster describe -o json | jq -cr '"\(.id) \(.rest_endpoint)"')
```

Example output:
```noformat
$ echo $CLUSTER_ID
lkc-onq9jx

$ echo $REST_ENDPOINT
https://pkc-921jm.us-east-2.aws.confluent.cloud:443
```

Next, generate base64-encoded Basic Auth credentials:
```shell
BASIC_CREDENTIALS=$(confluent api-key create --resource $CLUSTER_ID -o json \
                    | jq -cr '"\(.api_key):\(.api_secret)"' \
                    | base64)
```

Send the POST request using `curl`:
```shell
curl --request POST \
  -H 'Content-Type: application/json' \
  --url "$REST_ENDPOINT/kafka/v3/clusters/$CLUSTER_ID/topics/orders/records" \
  --header "Authorization: Basic $BASIC_CREDENTIALS" \
  --data-raw "{\"key\":{\"type\":\"STRING\",\"data\":\"k1\"}, \"value\":{\"type\":\"BINARY\",\"data\":\"$PAYLOAD\"}}"
```

## Consume the message

To consume the message, set the previously created API key as active in your CLI session:

```shell
API_KEY=$(confluent api-key list --current-user --resource $CLUSTER_ID -o json \
          | jq -cr '.[0].key')

confluent api-key use $API_KEY
```

Use the Confluent CLI and specify the value format as Avro:
```shell
confluent kafka topic consume orders \
    --value-format avro \
    --key-format string \
    --print-key \
    --from-beginning 
```

This prints:
```plaintext
k1	{"customer_id":8737,"product_id":19287,"product_name":"AirSwift Light Sneakers"}
```

## Clean up

1. Delete the API key created above::
   ```shell
   confluent api-key delete $API_KEY
   ```
2. Delete the Kafka cluster if you no longer need it:
   ```shell
   confluent kafka cluster delete $CLUSTER_ID
   ```
