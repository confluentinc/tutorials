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
