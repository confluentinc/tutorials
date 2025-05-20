<!-- title: How to handle deserialization errors in ksqlDB -->
<!-- description: In this tutorial, learn how to handle deserialization errors in ksqlDB, with step-by-step instructions and supporting code. -->

# How to handle deserialization errors in ksqlDB

How can you identify and manage deserialization errors that cause some events from a Kafka topic to not be written into a stream or table?


## Setup 

During the development of event streaming applications, it is common to have situations where some streams or tables are not receiving some events that have been sent to them. Often this happens because there was a deserialization error due to the event not being in the right format, but that is not so trivial to figure out. In this tutorial, we'll write a program that monitors a stream of sensors. Any deserialization error that happens in this stream will be made available in another stream that can be queried to check errors.

With the [KSQL_PROCESSING_LOG](https://docs.ksqldb.io/en/latest/reference/processing-log/#processing-log) you can run a query like the following
to track any deserialization errors that may have occurred:

```sql
SELECT
    message->deserializationError->errorMessage,
    encode(message->deserializationError->RECORDB64, 'base64', 'utf8') AS MSG,
    message->deserializationError->cause
  FROM KSQL_PROCESSING_LOG
  EMIT CHANGES;
```

## Running the example

### Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine

### Create Confluent Cloud resources

Login to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-cloud_kickstart
```

Run the following command to create a Confluent Cloud environment and Kafka cluster. This will create 
resources in AWS region `us-west-2` by default, but you may override these choices by passing the `--cloud` argument with
a value of `aws`, `gcp`, or `azure`, and the `--region` argument that is one of the cloud provider's supported regions,
which you can list by running `confluent kafka region list --cloud <CLOUD PROVIDER>`

```shell
confluent cloud-kickstart --name ksqldb-tutorial \
  --environment-name ksqldb-tutorial \
  --output-format stdout
```

Now, create a ksqlDB cluster by first getting your user ID of the form `u-123456` when you run this command:

```shell
confluent iam user list
```

And then create a ksqlDB cluster called `ksqldb-tutorial` with access linked to your user account:

```shell
confluent ksql cluster create ksqldb-tutorial \
  --credential-identity <USER ID>
```

### Run the commands

Login to the [Confluent Cloud Console](https://confluent.cloud/). Select `Environments` in the left-hand navigation,
and then click the `ksqldb-tutorial` environment tile. Click the `ksqldb-tutorial` Kafka cluster tile, and then select
`Topics` in the left-hand navigation. Create a topic called `sensors-raw` with 1 partition, and in the `Messages` tab,
produce the following two events, one at a time.

```noformat
{"id": "835226cf-caf6-4c91-a046-359f1d3a6e2e", "timestamp": "2020-01-15 02:25:30", "enabled": true}
```

```noformat
{"id": "1a076a64-4a84-40cb-a2e8-2190f3b37465", "timestamp": "2020-01-15 02:30:30", "enabled": "true"}
```

Next, select `ksqlDB` in the left-hand navigation.

The cluster may take a few minutes to be provisioned. Once its status is `Up`, click the cluster name and scroll down to the editor.

In the query properties section at the bottom, change the value for `auto.offset.reset` to `Earliest` so that ksqlDB 
will consume from the beginning of the stream we create.

Enter the following statement in the editor and click `Run query`. This will create a stream backed by the `sensors-raw`
topic. Note that the `enabled` field is of type `BOOLEAN`. The second event that we produced is not compliant as it 
includes a string Boolean value `"true"`.

```sql
CREATE STREAM sensors_raw (id VARCHAR, timestamp VARCHAR, enabled BOOLEAN)
  WITH (KAFKA_TOPIC = 'sensors-raw',
        VALUE_FORMAT = 'JSON',
        TIMESTAMP = 'TIMESTAMP',
        TIMESTAMP_FORMAT = 'yyyy-MM-dd HH:mm:ss',
        PARTITIONS = 1);
```

Now select from this stream in order to trigger a deserialization error:

```sql
SELECT *
FROM sensors_raw
EMIT CHANGES;
```

Finally, query the `KSQL_PROCESSING_LOG` stream to see the deserialization error:

```sql
SELECT
  message->deserializationError->errorMessage,
  encode(message->deserializationError->RECORDB64, 'base64', 'utf8') AS MSG,
  message->deserializationError->cause
FROM KSQL_PROCESSING_LOG
EMIT CHANGES;
```

The query output should look like this:

```plaintext
{
  "ERRORMESSAGE": "Failed to deserialize value from topic: sensors-raw. Can't convert type. sourceType: TextNode, requiredType: BOOLEAN, path: $.ENABLED",
  "MSG": "{\"id\":\"1a076a64-4a84-40cb-a2e8-2190f3b37465\",\"timestamp\":\"2020-01-15 02:30:30\",\"enabled\":\"true\"}",
  "CAUSE": [
    "Can't convert type. sourceType: TextNode, requiredType: BOOLEAN, path: $.ENABLED",
    "Can't convert type. sourceType: TextNode, requiredType: BOOLEAN, path: .ENABLED",
    "Can't convert type. sourceType: TextNode, requiredType: BOOLEAN"
  ]
}
```

### Clean up

When you are finished, delete the `ksqldb-tutorial` environment by first getting the environment ID of the form 
`env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```
