<!-- title: How to mask data with ksqlDB -->
<!-- description: In this tutorial, learn how to mask data with ksqlDB, with step-by-step instructions and supporting code. -->

# How to mask data with ksqlDB

Suppose you have a topic that contains personally identifiable information (PII), and you want to mask those fields. In this tutorial, we'll write a program that persists the events in the original topic to a new Kafka topic with the PII obfuscated.

## Setup

First, create a stream over the topic containing the PII data:

```sql
CREATE STREAM purchases (order_id INT, customer_name VARCHAR, date_of_birth VARCHAR,
                         product VARCHAR, order_total_usd DOUBLE, town VARCHAR, country VARCHAR)
    WITH (KAFKA_TOPIC='purchases',
          PARTITIONS=1,
          VALUE_FORMAT='JSON');
```

Then create a stream that will mask the PII columns using the ksqlDB [MASK](https://docs.ksqldb.io/en/0.8.1-ksqldb/developer-guide/ksqldb-reference/scalar-functions/#mask) function:

```sql
CREATE STREAM purchases_pii_obfuscated
    WITH (KAFKA_TOPIC='purchases_pii_obfuscated', VALUE_FORMAT='JSON', PARTITIONS=1) AS
    SELECT MASK(customer_name) AS customer_name,
           MASK(date_of_birth) AS date_of_birth,
           order_id, product, order_total_usd, town, country
    FROM purchases;
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

Run the following SQL statements to create the `purchases` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM purchases (order_id INT, customer_name VARCHAR, date_of_birth VARCHAR,
                         product VARCHAR, order_total_usd DOUBLE, town VARCHAR, country VARCHAR)
    WITH (KAFKA_TOPIC='purchases',
          PARTITIONS=1,
          VALUE_FORMAT='JSON');
```

```sql
INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
    VALUES (1, 'Britney', '02/29/2000', 'Heart Rate Monitor', 119.93, 'Denver', 'USA');
INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
    VALUES (2, 'Michael', '06/08/1981', 'Foam Roller', 34.95, 'Los Angeles', 'USA');
INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
    VALUES (3, 'Kimmy', '05/19/1978', 'Hydration Belt', 50.00, 'Tuscan', 'USA');
INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
    VALUES (4, 'Samantha', '08/05/1983', 'Wireless Headphones', 175.93, 'Tulsa', 'USA');
```

Next, create a new stream from the `purchases` stream with PII data masked. 
Note that we first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

CREATE STREAM purchases_pii_obfuscated
    WITH (KAFKA_TOPIC='purchases_pii_obfuscated', VALUE_FORMAT='JSON', PARTITIONS=1) AS
    SELECT MASK(customer_name) AS customer_name,
           MASK(date_of_birth) AS date_of_birth,
           order_id, product, order_total_usd, town, country
    FROM purchases;
```

Query the new stream:

```sql
SELECT * FROM purchases_pii_obfuscated;
```

The query output should look like this:

```plaintext
+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
|CUSTOMER_NAME            |DATE_OF_BIRTH            |ORDER_ID                 |PRODUCT                  |ORDER_TOTAL_USD          |TOWN                     |COUNTRY                  |
+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
|Xxxxxxx                  |nn-nn-nnnn               |1                        |Heart Rate Monitor       |119.93                   |Denver                   |USA                      |
|Xxxxxxx                  |nn-nn-nnnn               |2                        |Foam Roller              |34.95                    |Los Angeles              |USA                      |
|Xxxxx                    |nn-nn-nnnn               |3                        |Hydration Belt           |50.0                     |Tuscan                   |USA                      |
|Xxxxxxxx                 |nn-nn-nnnn               |4                        |Wireless Headphones      |175.93                   |Tulsa                    |USA                      |
+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
