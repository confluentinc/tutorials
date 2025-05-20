<!-- title: How to convert a timestamp into a different time zone with ksqlDB -->
<!-- description: In this tutorial, learn how to convert a timestamp into a different time zone with ksqlDB, with step-by-step instructions and supporting code. -->

# How to convert a timestamp into a different time zone with ksqlDB

Suppose you want to create reports from a table and all the timestamps must be in a particular time zone, which happens to be different from the timezone of the Kafka data source. This tutorial shows how you can convert timestamp data into another timezone.

## Setup

You'll start with a stream of temperature readings sourced from a Kafka topic named `device-events`.  The timestamps are in Unix time format of a `long` which is a `BIGINT` in ksqlDB.

```sql
CREATE STREAM temperature_readings_raw (event_time BIGINT, temperature INT)
    WITH (KAFKA_TOPIC='device-events',
          VALUE_FORMAT='JSON');
```

## Convert to a different time zone

In order to convert this column to timestamp format and in a particular time zone (`America/Denver`), first convert
`event_time` from a `BIGINT` to a timestamp with the `FROM_UNIXTIME` function. Then the `CONVERT_TZ` function
uses the result to produce a timestamp in the desired time zone.

```sql
SELECT temperature,CONVERT_TZ(FROM_UNIXTIME(event_time), 'UTC', 'America/Denver') AS event_time_mt
FROM temperature_readings_raw
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

  Next, open the ksqlDB CLI:

  ```shell
  docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
  ```

  Run the following SQL statements to create the `temperature_readings_raw` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM temperature_readings_raw (event_time BIGINT, temperature INT)
      WITH (KAFKA_TOPIC='device-events',
            PARTITIONS=1,
            VALUE_FORMAT='JSON');
  ```

  ```sql
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615566394751, 100);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615566401534, 132);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567732840, 144);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567735866, 103);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567736875, 102);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567738890, 101);
  ```

  Finally, run the timestamp conversion query. Note that we first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  SELECT temperature,
         CONVERT_TZ(FROM_UNIXTIME(event_time), 'UTC', 'America/Denver') AS event_time_mt
  FROM temperature_readings_raw
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------------------+-------------------------------------+
  |TEMPERATURE                          |EVENT_TIME_MT                        |
  +-------------------------------------+-------------------------------------+
  |100                                  |2021-03-12T09:26:34.751              |
  |132                                  |2021-03-12T09:26:41.534              |
  |144                                  |2021-03-12T09:48:52.840              |
  |103                                  |2021-03-12T09:48:55.866              |
  |102                                  |2021-03-12T09:48:56.875              |
  |101                                  |2021-03-12T09:48:58.890              |
  +-------------------------------------+-------------------------------------+
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

  Login to the [Confluent Cloud Console](https://confluent.cloud/). Select `Environments` in the left-hand navigation,
  and then click the `ksqldb-tutorial` environment tile. Click the `ksqldb-tutorial` Kafka cluster tile, and then
  select `ksqlDB` in the left-hand navigation.

  The cluster may take a few minutes to be provisioned. Once its status is `Up`, click the cluster name and scroll down to the editor.

  In the query properties section at the bottom, change the value for `auto.offset.reset` to `Earliest` so that ksqlDB 
  will consume from the beginning of the stream we create.

  Enter the following statements in the editor and click `Run query`. This creates the `temperature_readings_raw` stream and
  populates it with test data.

  ```sql
  CREATE STREAM temperature_readings_raw (event_time BIGINT, temperature INT)
      WITH (KAFKA_TOPIC='device-events',
            PARTITIONS=1,
            VALUE_FORMAT='JSON');

  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615566394751, 100);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615566401534, 132);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567732840, 144);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567735866, 103);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567736875, 102);
  INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567738890, 101);
  ```

  Now paste the timestamp conversion query in the editor and click `Run query`:

  ```sql
  SELECT temperature,
         CONVERT_TZ(FROM_UNIXTIME(event_time), 'UTC', 'America/Denver') AS event_time_mt
  FROM temperature_readings_raw
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------------------+-------------------------------------+
  |TEMPERATURE                          |EVENT_TIME_MT                        |
  +-------------------------------------+-------------------------------------+
  |100                                  |2021-03-12T09:26:34.751              |
  |132                                  |2021-03-12T09:26:41.534              |
  |144                                  |2021-03-12T09:48:52.840              |
  |103                                  |2021-03-12T09:48:55.866              |
  |102                                  |2021-03-12T09:48:56.875              |
  |101                                  |2021-03-12T09:48:58.890              |
  +-------------------------------------+-------------------------------------+
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
