<!-- title: How to concatenate column values in ksqlDB -->
<!-- description: In this tutorial, learn how to concatenate column values in ksqlDB, with step-by-step instructions and supporting code. -->

# How to concatenate column values in ksqlDB

In this tutorial, we'll show how to use the concatenation operator in ksqlDB to create a single value from multiple columns.

## Setup

Assume that we have a stream named `activity_stream` that simulates stock purchases and serves as our example for concatenating
column values.

```sql
CREATE STREAM activity_stream (
        num_shares INT,
        amount DOUBLE,
        txn_ts VARCHAR,
        first_name VARCHAR,
        last_name  VARCHAR,
        symbol VARCHAR)
    WITH (KAFKA_TOPIC='activity-stream',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

## Concatenate column values 

Now let's create a stream that concatenates several columns from `activity_stream` to create a summary of activity.

```sql
CREATE STREAM activity_summary AS
  SELECT first_name + ' ' + last_name +
       ' purchased ' +
       CAST(num_shares AS VARCHAR) +
       ' shares of ' +
       symbol AS summary
  FROM activity_stream
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

  Run the following SQL statements to create the `activity_stream` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM activity_stream (
          num_shares INT,
          amount DOUBLE,
          txn_ts VARCHAR,
          first_name VARCHAR,
          last_name  VARCHAR,
          symbol VARCHAR)
      WITH (KAFKA_TOPIC='activity-stream',
            VALUE_FORMAT='JSON',
            PARTITIONS=1);

  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (300, 5004.89, '2024-09-04 02:35:43', 'Tony', 'Stark', 'IMEP');
  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (425, 1000.91, '2024-09-04 02:35:44', 'Nick', 'Fury', 'IMEP');
  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (918, 8004.54, '2024-09-04 02:35:45', 'Natasha', 'Romanov', 'STRK');
  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (100, 6088.22, '2024-09-04 02:35:46', 'Wanda', 'Maximoff', 'STRK');
  ```

  Finally, run the concatenation query and then select from it. Note that we first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM activity_summary AS
    SELECT first_name + ' ' + last_name +
         ' purchased ' +
         CAST(num_shares AS VARCHAR) +
         ' shares of ' +
         symbol AS summary
    FROM activity_stream
    EMIT CHANGES;
  ```

  ```sql
  SELECT *
  FROM activity_summary
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-----------------------------------------------------------+
  |SUMMARY                                                    |
  +-----------------------------------------------------------+
  |Tony Stark purchased 30000 shares of IMEP                  |
  |Nick Fury purchased 425 shares of IMEP                     |
  |Natasha Romanov purchased 918 shares of STRK               |
  |Wanda Maximoff purchased 100 shares of STRK                |
  +-----------------------------------------------------------+
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

  Enter the following statements in the editor and click `Run query`. This creates the `activity_stream` stream and
  populates it with test data.

  ```sql
  CREATE STREAM activity_stream (
          num_shares INT,
          amount DOUBLE,
          txn_ts VARCHAR,
          first_name VARCHAR,
          last_name  VARCHAR,
          symbol VARCHAR)
      WITH (KAFKA_TOPIC='activity-stream',
            VALUE_FORMAT='JSON',
            PARTITIONS=1);

  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (300, 5004.89, '2024-09-04 02:35:43', 'Tony', 'Stark', 'IMEP');
  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (425, 1000.91, '2024-09-04 02:35:44', 'Nick', 'Fury', 'IMEP');
  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (918, 8004.54, '2024-09-04 02:35:45', 'Natasha', 'Romanov', 'STRK');
  INSERT INTO activity_stream (num_shares, amount, txn_ts, first_name, last_name, symbol)
      VALUES (100, 6088.22, '2024-09-04 02:35:46', 'Wanda', 'Maximoff', 'STRK');
  ```

  Now paste the query to concatenate column values in the editor and click `Run query`:

  ```sql
  CREATE STREAM activity_summary AS
    SELECT first_name + ' ' + last_name +
         ' purchased ' +
         CAST(num_shares AS VARCHAR) +
         ' shares of ' +
         symbol AS summary
    FROM activity_stream
    EMIT CHANGES;
  ```

  Finally, select from the resulting stream:
  ```sql
  SELECT *
  FROM activity_summary
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-----------------------------------------------------------+
  |SUMMARY                                                    |
  +-----------------------------------------------------------+
  |Tony Stark purchased 30000 shares of IMEP                  |
  |Nick Fury purchased 425 shares of IMEP                     |
  |Natasha Romanov purchased 918 shares of STRK               |
  |Wanda Maximoff purchased 100 shares of STRK                |
  +-----------------------------------------------------------+
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
