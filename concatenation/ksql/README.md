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
