<!-- title: How to compute the difference between two columns with ksqlDB -->
<!-- description: In this tutorial, learn to compute the difference between two columns with ksqlDB, with step-by-step instructions and supporting code. -->

# How to compute the difference between two columns with ksqlDB

Suppose you have a topic with events that represent a customer's purchase history, where each event also includes the 
amount of the customer's previous purchase. In this tutorial, we will use ksqlDB to calculate the difference between the
current and previous purchase amounts.

## Setup

Assume a stream named `customer_purchases` in which each event contains the amounts of the customer's current and 
previous purchases:

```sql
CREATE STREAM customer_purchases (
        id VARCHAR,
        current_purchase DOUBLE,
        previous_purchase DOUBLE,
        txn_ts VARCHAR,
        first_name VARCHAR,
        last_name  VARCHAR)
    WITH (KAFKA_TOPIC='customer_purchases',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

## Calculate the difference between two columns

Now create a query to determine the difference between two columns:

```sql
SELECT first_name,
       last_name,
       current_purchase - previous_purchase as purchase_diff
FROM customer_purchases
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

Run the following SQL statements to create the `customer_purchases` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM customer_purchases (
        current_purchase DOUBLE,
        previous_purchase DOUBLE,
        txn_ts VARCHAR,
        first_name VARCHAR,
        last_name  VARCHAR)
    WITH (KAFKA_TOPIC='customer-purchases',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

```sql
INSERT INTO customer_purchases (current_purchase, previous_purchase, txn_ts, first_name, last_name)
    VALUES (50.89, 28.99, '2024-08-04 02:35:43', 'Tony', 'Stark');
INSERT INTO customer_purchases (current_purchase, previous_purchase, txn_ts, first_name, last_name)
    VALUES (23.12, 12.99, '2024-08-04 02:35:44', 'Nick', 'Fury');
INSERT INTO customer_purchases (current_purchase, previous_purchase, txn_ts, first_name, last_name)
    VALUES (20.00, 6.99, '2024-08-04 02:35:45', 'Natasha', 'Romanov');
INSERT INTO customer_purchases (current_purchase, previous_purchase, txn_ts, first_name, last_name)
    VALUES (99.29, 1.99, '2024-08-04 02:35:46', 'Wanda', 'Maximoff');
```

Finally, run the column difference query. Note that we first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

SELECT first_name,
       last_name,
       current_purchase - previous_purchase as purchase_diff
FROM customer_purchases
EMIT CHANGES;
```

The query output should look like this:

```plaintext
+-------------------+-------------------+-------------------+
|FIRST_NAME         |LAST_NAME          |PURCHASE_DIFF      |
+-------------------+-------------------+-------------------+
|Tony               |Stark              |21.90              |
|Nick               |Fury               |10.13              |
|Natasha            |Romanov            |13.01              |
|Wanda              |Maximoff           |97.30              |
+-------------------+-------------------+-------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
