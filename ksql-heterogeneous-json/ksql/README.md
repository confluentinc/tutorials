<!-- title: How to handle heterogeneous JSON with ksqlDB -->
<!-- description: In this tutorial, learn how to handle heterogeneous JSON with ksqlDB, with step-by-step instructions and supporting code. -->

# How to handle heterogeneous JSON with ksqlDB

Suppose you have a topic with records formatted in JSON, but not all the records have the same structure and value types. 
In this tutorial, we'll demonstrate how to work with JSON of different structures.

## Set Up

For context, imagine you have three different JSON formats in a Kafka topic:

```json
  "JSONType1": {
    "fieldA": "some data",
    "numberField": 1.001,
    "oneOnlyField": "more data", 
    "randomField": "random data"
  }
```
```json
  "JSONType2": {
    "fieldA": "data",
    "fieldB": "b-data",
    "numberField": 98.6 
  }
```
```json
  "JSONType3": {
    "fieldA": "data",
    "fieldB": "b-data",
    "numberField": 98.6,
    "fieldC": "data",
    "fieldD": "D-data"    
  }
```

From these three different JSON structures you want to extract `oneOnlyField`, `numberField`, and `fieldD` from `JSONType`, `JSONType2`, and `JSONType3` respectively.

Your first step is to create a stream and use a `VARCHAR` keyword to define the outermost element of the JSON types.

```sql
CREATE STREAM data_stream (
    JSONType1 VARCHAR,
    JSONType2 VARCHAR,
    JSONType3 VARCHAR
) WITH (KAFKA_TOPIC='data_stream',
        VALUE_FORMAT='JSON',
        PARTITIONS=1);
```

Then you can access the fields using the `EXTRACTJSONFIELD` keyword and cast into the appropriate types by selecting from `data_stream`:

```sql
SELECT EXTRACTJSONFIELD (JSONType1, '$.oneOnlyField') AS special_info,
       CAST(EXTRACTJSONFIELD (JSONType2, '$.numberField') AS DOUBLE) AS runfld,
       EXTRACTJSONFIELD (JSONType3, '$.fieldD') AS description
FROM data_stream
EMIT CHANGES;
```

## Running the example

You can run the example backing this tutorial in one of two ways: locally with the `ksql` CLI against Kafka and ksqlDB running in Docker, or with Confluent Cloud.

<details>
  <summary>Local With Docker</summary>

  ### Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

  ### Run the commands

  Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

  Start ksqlDB and Kafka:

  ```shell
  docker compose -f ./docker/docker-compose-ksqldb.yml up -d
  ```

  Create the `data_stream` topic:

  ```shell
  docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --create --topic data_stream
  ```

  Open a console producer:

  ```shell
  docker exec -it broker kafka-console-producer --bootstrap-server localhost:29092 --topic data_stream
  ```

  Ever the following four events at the prompt:

  ```json
  { "JSONType1": { "fieldA": "some data", "numberField": 1.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data" }}
  { "JSONType1": { "fieldA": "some data", "numberField": 2.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 99.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data-2" }}
  { "JSONType1": { "fieldA": "some data", "numberField": 3.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 100.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data-3" }}
  { "JSONType1": { "fieldA": "some data", "numberField": 4.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 101.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data-4" }}
  ```
  Next, open the ksqlDB CLI:

  ```shell
  docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
  ```

  Enter the following statement. This will create a stream backed by the `data_stream` topic.

  ```sql
  CREATE STREAM data_stream (
      JSONType1 VARCHAR,
      JSONType2 VARCHAR,
      JSONType3 VARCHAR
  ) WITH (KAFKA_TOPIC='data_stream',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
  ```

  Now you can access the fields using the `EXTRACTJSONFIELD` function. Note that we first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  SELECT EXTRACTJSONFIELD (JSONType1, '$.oneOnlyField') AS special_info,
         CAST(EXTRACTJSONFIELD (JSONType2, '$.numberField') AS DOUBLE) AS runfld,
         EXTRACTJSONFIELD (JSONType3, '$.fieldD') AS description
  FROM data_stream
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +------------------------+------------------------+------------------------+
  |SPECIAL_INFO            |RUNFLD                  |DESCRIPTION             |
  +------------------------+------------------------+------------------------+
  |more data               |98.6                    |D-data                  |
  |more data               |99.6                    |D-data-2                |
  |more data               |100.6                   |D-data-3                |
  |more data               |101.6                   |D-data-4                |
  +------------------------+------------------------+------------------------+
  ```

  When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

  ```shell
  docker compose -f ./docker/docker-compose-ksqldb.yml down
  ```

</details>

<details>
  <summary>Confluent Cloud</summary>

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

  Login to the [Confluent Cloud Console](https://confluent.cloud/). Select `Environments` in the lefthand navigation,
  and then click the `ksqldb-tutorial` environment tile. Click the `ksqldb-tutorial` Kafka cluster tile, and then select
  `Topics` in the lefthand navigation. Create a topic called `data_stream` with 1 partition, and in the `Messages` tab,
  produce the following four events as the `Value`, one at a time.

  ```noformat
  {
    "JSONType1": {
      "fieldA": "some data",
      "numberField": 1.001,
      "oneOnlyField": "more data",
      "randomField": "random data"
    },
    "JSONType2": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 98.6
    },
    "JSONType3": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 98.6,
      "fieldC": "data",
      "fieldD": "D-data"
    }
  }
  ```

  ```noformat
  {
    "JSONType1": {
      "fieldA": "some data",
      "numberField": 2.001,
      "oneOnlyField": "more data",
      "randomField": "random data"
    },
    "JSONType2": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 99.6
    },
    "JSONType3": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 98.6,
      "fieldC": "data",
      "fieldD": "D-data-2"
    }
  }
  ```

  ```noformat
  {
    "JSONType1": {
      "fieldA": "some data",
      "numberField": 3.001,
      "oneOnlyField": "more data",
      "randomField": "random data"
    },
    "JSONType2": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 100.6
    },
    "JSONType3": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 98.6,
      "fieldC": "data",
      "fieldD": "D-data-3"
    }
  }
  ```

  ```noformat
  {
    "JSONType1": {
      "fieldA": "some data",
      "numberField": 4.001,
      "oneOnlyField": "more data",
      "randomField": "random data"
    },
    "JSONType2": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 101.6
    },
    "JSONType3": {
      "fieldA": "data",
      "fieldB": "b-data",
      "numberField": 98.6,
      "fieldC": "data",
      "fieldD": "D-data-4"
    }
  }
  ```

  Next, select `ksqlDB` in the lefthand navigation.

  The cluster may take a few minutes to be provisioned. Once its status is `Up`, click the cluster name and scroll down to the editor.

  In the query properties section at the bottom, change the value for `auto.offset.reset` to `Earliest` so that ksqlDB 
  will consume from the beginning of the stream we create.

  Enter the following statement in the editor and click `Run query`. This will create a stream backed by the `data_stream` topic.

  ```sql
  CREATE STREAM data_stream (
      JSONType1 VARCHAR,
      JSONType2 VARCHAR,
      JSONType3 VARCHAR
  ) WITH (KAFKA_TOPIC='data_stream',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
  ```

  Now you can access the fields using the `EXTRACTJSONFIELD` function:

  ```sql
  SELECT EXTRACTJSONFIELD (JSONType1, '$.oneOnlyField') AS special_info,
         CAST(EXTRACTJSONFIELD (JSONType2, '$.numberField') AS DOUBLE) AS runfld,
         EXTRACTJSONFIELD (JSONType3, '$.fieldD') AS description
  FROM data_stream
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +------------------------+------------------------+------------------------+
  |SPECIAL_INFO            |RUNFLD                  |DESCRIPTION             |
  +------------------------+------------------------+------------------------+
  |more data               |98.6                    |D-data                  |
  |more data               |99.6                    |D-data-2                |
  |more data               |100.6                   |D-data-3                |
  |more data               |101.6                   |D-data-4                |
  +------------------------+------------------------+------------------------+
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

</details>
