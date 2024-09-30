<!-- title: How to use a join in ksqlDB for anomaly detection -->
<!-- description: In this tutorial, learn how to use a join in ksqlDB for anomaly detection, with step-by-step instructions and supporting code. -->

# How to use a join in ksqlDB for anomaly detection

This tutorial gives examples of using a Stream-Table join to populate a table and then use windowing on the result table.  The use case for this tutorial 
is alerting of suspicious financial transactions over a 24-hour period.

## Setup

First, we'll need to create a stream of transactions:

```sql
CREATE STREAM transactions (txn_id BIGINT, username VARCHAR, recipient VARCHAR, amount DOUBLE, ts VARCHAR)
    WITH (KAFKA_TOPIC='transactions',
          PARTITIONS=1,
          VALUE_FORMAT='JSON',
          TIMESTAMP='ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss');
```

Then create a table of known suspicious names:

```sql
CREATE TABLE suspicious_names (created_ts VARCHAR,
                               company_name VARCHAR PRIMARY KEY,
                               company_id INT)
    WITH (KAFKA_TOPIC='suspicious_names',
          PARTITIONS=1,
          VALUE_FORMAT='JSON',
          TIMESTAMP='created_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss');
```

Now create the `suspicious_transactions` table by joining the `transactions` stream with the `suspicious_accounts` table:

```sql
CREATE STREAM suspicious_transactions
    WITH (KAFKA_TOPIC='suspicious_transactions', PARTITIONS=1, VALUE_FORMAT='JSON') AS
    SELECT T.txn_id, T.username, T.recipient, T.amount, T.ts
    FROM transactions T
    INNER JOIN
    suspicious_names S
    ON T.recipient = S.company_name;
```

Finally, we'll wrap it all up in a table that captures activity with 3 or more suspicious transactions in a 24-hour period:

```sql
CREATE TABLE accounts_to_monitor
    WITH (KAFKA_TOPIC='accounts_to_monitor', PARTITIONS=1, VALUE_FORMAT='JSON') AS
    SELECT TIMESTAMPTOSTRING(WINDOWSTART, 'yyyy-MM-dd HH:mm:ss Z') AS window_start, 
           TIMESTAMPTOSTRING(WINDOWEND, 'yyyy-MM-dd HH:mm:ss Z') AS window_end,
           username
    FROM suspicious_transactions
    WINDOW TUMBLING (SIZE 24 HOURS) 
    GROUP BY username
    HAVING COUNT(*) > 3;
```

The fields `window_start` and `window_end` tell us the time interval during which suspicious activity occurred. The `WINDOW TUMBLING` part of the query 
allows us to do an aggregation with distinct time boundaries. 
In this case, our window is fixed at a length of 24 hours, does not allow gaps, and does not allow overlapping.

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

  Run the following SQL statements to create the `transactions` stream and `suspicious_names` table backed by Kafka 
  running in Docker and populate them with test data.

  ```sql
  CREATE STREAM transactions (txn_id BIGINT, username VARCHAR, recipient VARCHAR, amount DOUBLE, ts VARCHAR)
      WITH (KAFKA_TOPIC='transactions',
            PARTITIONS=1,
            VALUE_FORMAT='JSON',
            TIMESTAMP='ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss');
  ```

  ```sql
  CREATE TABLE suspicious_names (created_ts VARCHAR,
                                 company_name VARCHAR PRIMARY KEY,
                                 company_id INT)
      WITH (KAFKA_TOPIC='suspicious_names',
            PARTITIONS=1,
            VALUE_FORMAT='JSON',
            TIMESTAMP='created_ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss');
  ```

  ```sql
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9900, 'Abby Normal', 'Verizon', 22.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 2 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (12, 'Victor von Frankenstein', 'Tattered Cover', 7.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 3 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (13, 'Frau Blücher', 'Peebles', 70.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 4 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9903, 'Abby Normal', 'Verizon', 61.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 5 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9901, 'Abby Normal', 'Spirit Halloween', 83.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 6 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9902, 'Abby Normal', 'Spirit Halloween', 46.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 7 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9904, 'Abby Normal', 'Spirit Halloween', 59.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 8 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (6, 'Victor von Frankenstein', 'Confluent Cloud', 21.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 9 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (18, 'Frau Blücher', 'Target', 70.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 10 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (7, 'Victor von Frankenstein', 'Verizon', 100.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 11 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (19, 'Frau Blücher', 'Goodwill', 7.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 12 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  ```

  ```sql
  INSERT INTO suspicious_names (CREATED_TS, COMPANY_NAME, COMPANY_ID) VALUES (FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (5 * 24 * 60 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'), 'Verizon', 1);
  INSERT INTO suspicious_names (CREATED_TS, COMPANY_NAME, COMPANY_ID) VALUES (FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (4 * 24 * 60 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'), 'Spirit Halloween', 2);
  INSERT INTO suspicious_names (CREATED_TS, COMPANY_NAME, COMPANY_ID) VALUES (FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (3 * 24 * 60 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'), 'Best Buy', 3);
  ```

  Finally, run the queries to find suspicious transactions and flag accounts. Note that we first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM suspicious_transactions
      WITH (KAFKA_TOPIC='suspicious_transactions', PARTITIONS=1, VALUE_FORMAT='JSON') AS
      SELECT T.txn_id, T.username, T.recipient, T.amount, T.ts
      FROM transactions T
      INNER JOIN
      suspicious_names S
      ON T.recipient = S.company_name;

  CREATE TABLE accounts_to_monitor
      WITH (KAFKA_TOPIC='accounts_to_monitor', PARTITIONS=1, VALUE_FORMAT='JSON') AS
      SELECT TIMESTAMPTOSTRING(WINDOWSTART, 'yyyy-MM-dd HH:mm:ss Z') AS window_start, 
             TIMESTAMPTOSTRING(WINDOWEND, 'yyyy-MM-dd HH:mm:ss Z') AS window_end,
             username
      FROM suspicious_transactions
      WINDOW TUMBLING (SIZE 24 HOURS) 
      GROUP BY username
      HAVING COUNT(*) > 3;
      
  SELECT *
  FROM accounts_to_monitor
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
  |USERNAME                 |WINDOWSTART              |WINDOWEND                |WINDOW_START             |WINDOW_END               |
  +-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
  |Abby Normal              |1726963200000            |1727049600000            |2024-09-22 00:00:00 +0000|2024-09-23 00:00:00 +0000|
  +-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
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
  and then click the `ksqldb-tutorial` environment tile. Click the `ksqldb-tutorial` Kafka cluster tile, and then
  select `ksqlDB` in the lefthand navigation.

  The cluster may take a few minutes to be provisioned. Once its status is `Up`, click the cluster name and scroll down to the editor.

  In the query properties section at the bottom, change the value for `auto.offset.reset` to `Earliest` so that ksqlDB 
  will consume from the beginning of the stream we create.

  Enter the following statements in the editor and click `Run query`. This creates the `transactions` stream and
  `suspicious_names` table and populates them with test data.

  ```sql
  CREATE STREAM transactions (txn_id BIGINT, username VARCHAR, recipient VARCHAR, amount DOUBLE, ts VARCHAR)
      WITH (KAFKA_TOPIC='transactions',
            PARTITIONS=1,
            VALUE_FORMAT='JSON',
            TIMESTAMP='ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss');

  CREATE TABLE suspicious_names (created_ts VARCHAR,
                                 company_name VARCHAR PRIMARY KEY,
                                 company_id INT)
      WITH (KAFKA_TOPIC='suspicious_names',
            PARTITIONS=1,
            VALUE_FORMAT='JSON',
            TIMESTAMP='created_ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss');

  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9900, 'Abby Normal', 'Verizon', 22.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 2 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (12, 'Victor von Frankenstein', 'Tattered Cover', 7.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 3 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (13, 'Frau Blücher', 'Peebles', 70.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 4 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9903, 'Abby Normal', 'Verizon', 61.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 5 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9901, 'Abby Normal', 'Spirit Halloween', 83.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 6 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9902, 'Abby Normal', 'Spirit Halloween', 46.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 7 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (9904, 'Abby Normal', 'Spirit Halloween', 59.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 8 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (6, 'Victor von Frankenstein', 'Confluent Cloud', 21.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 9 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (18, 'Frau Blücher', 'Target', 70.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 10 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (7, 'Victor von Frankenstein', 'Verizon', 100.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 11 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));
  INSERT INTO transactions (TXN_ID, USERNAME, RECIPIENT, AMOUNT, TS) VALUES (19, 'Frau Blücher', 'Goodwill', 7.0, FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (1 * 24 * 60 * 60 * 1000 + 12 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'));

  INSERT INTO suspicious_names (CREATED_TS, COMPANY_NAME, COMPANY_ID) VALUES (FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (5 * 24 * 60 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'), 'Verizon', 1);
  INSERT INTO suspicious_names (CREATED_TS, COMPANY_NAME, COMPANY_ID) VALUES (FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (4 * 24 * 60 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'), 'Spirit Halloween', 2);
  INSERT INTO suspicious_names (CREATED_TS, COMPANY_NAME, COMPANY_ID) VALUES (FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() - (3 * 24 * 60 * 60 * 1000)),'yyyy-MM-dd HH:mm:ss'), 'Best Buy', 3);
  ```

  Now paste the query to find suspicious transactions and click `Run query`:

  ```sql
  CREATE STREAM suspicious_transactions
      WITH (KAFKA_TOPIC='suspicious_transactions', PARTITIONS=1, VALUE_FORMAT='JSON') AS
      SELECT T.txn_id, T.username, T.recipient, T.amount, T.ts
      FROM transactions T
      INNER JOIN
      suspicious_names S
      ON T.recipient = S.company_name;
  ```

  Finally, create the table of accounts to monitor:

  ```sql
  CREATE TABLE accounts_to_monitor
      WITH (KAFKA_TOPIC='accounts_to_monitor', PARTITIONS=1, VALUE_FORMAT='JSON') AS
      SELECT TIMESTAMPTOSTRING(WINDOWSTART, 'yyyy-MM-dd HH:mm:ss Z') AS window_start, 
             TIMESTAMPTOSTRING(WINDOWEND, 'yyyy-MM-dd HH:mm:ss Z') AS window_end,
             username
      FROM suspicious_transactions
      WINDOW TUMBLING (SIZE 24 HOURS) 
      GROUP BY username
      HAVING COUNT(*) > 3;
  ```

  Query the `accounts_to_monitor` table:

  ```sql
  SELECT *
  FROM accounts_to_monitor
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
  |USERNAME                 |WINDOWSTART              |WINDOWEND                |WINDOW_START             |WINDOW_END               |
  +-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
  |Abby Normal              |1726963200000            |1727049600000            |2024-09-22 00:00:00 +0000|2024-09-23 00:00:00 +0000|
  +-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
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
