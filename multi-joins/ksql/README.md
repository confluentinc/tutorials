<!-- title: How to join multiple streams and tables with ksqlDB -->
<!-- description: In this tutorial, learn how to join multiple streams and tables with ksqlDB, with step-by-step instructions and supporting code. -->

# How to join multiple streams and tables with ksqlDB

In this tutorial, we demonstrate how to join multiple streams and tables together using an example from retail sales.

## Setup

For this example, let's say you have 2 tables `customers` and `items` and a stream `orders` and you want to do a join between all three to enrich the `orders` stream with more complete information.

Here are the table definitions:
   
```sql
CREATE TABLE customers (customer_id STRING PRIMARY KEY, customer_name STRING)
    WITH (KAFKA_TOPIC='customers',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

```sql
CREATE TABLE items (item_id STRING PRIMARY KEY, item_name STRING)
    WITH (KAFKA_TOPIC='items',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

And here is the stream definition:
```sql
CREATE STREAM orders (order_id STRING KEY, customer_id STRING, item_id STRING, purchase_date STRING)
    WITH (KAFKA_TOPIC='orders',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

Now, to create an enriched order stream, you'll have an SQL statement like this:
```sql
CREATE STREAM orders_enriched AS
  SELECT customers.customer_id AS customer_id, customers.customer_name AS customer_name,
         orders.order_id, orders.purchase_date,
         items.item_id, items.item_name
  FROM orders
  LEFT JOIN customers on orders.customer_id = customers.customer_id
  LEFT JOIN items on orders.item_id = items.item_id;
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

Run the following SQL statements to create the `orders` stream and `customers` and `items` tables backed by Kafka running in Docker and 
populate them with test data.

```sql
CREATE STREAM orders (order_id STRING KEY, customer_id STRING, item_id STRING, purchase_date STRING)
    WITH (KAFKA_TOPIC='orders',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

```sql
CREATE TABLE customers (customer_id STRING PRIMARY KEY, customer_name STRING)
    WITH (KAFKA_TOPIC='customers',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

```sql
CREATE TABLE items (item_id STRING PRIMARY KEY, item_name STRING)
    WITH (KAFKA_TOPIC='items',
          VALUE_FORMAT='JSON',
          PARTITIONS=1);
```

```sql
INSERT INTO items VALUES ('101', 'Television 60-in');
INSERT INTO items VALUES ('102', 'Laptop 15-in');
INSERT INTO items VALUES ('103', 'Speakers');

INSERT INTO customers VALUES ('1', 'Adrian Garcia');
INSERT INTO customers VALUES ('2', 'Robert Miller');
INSERT INTO customers VALUES ('3', 'Brian Smith');

INSERT INTO orders VALUES ('abc123', '1', '101', '2024-09-01');
INSERT INTO orders VALUES ('abc345', '1', '102', '2024-09-01');
INSERT INTO orders VALUES ('abc678', '2', '101', '2024-09-01');
INSERT INTO orders VALUES ('abc987', '3', '101', '2024-09-03');
INSERT INTO orders VALUES ('xyz123', '2', '103', '2024-09-03');
INSERT INTO orders VALUES ('xyz987', '2', '102', '2024-09-05');
```

Finally, run the stream-table-table join query and land the results in a new `order_enriched` stream. Note that we first
tell ksqlDB to consume from the beginning of the streams.

```sql
SET 'auto.offset.reset'='earliest';

CREATE STREAM orders_enriched AS
    SELECT customers.customer_id AS customer_id, customers.customer_name AS customer_name,
           orders.order_id, orders.purchase_date,
           items.item_id, items.item_name
    FROM orders
    LEFT JOIN customers on orders.customer_id = customers.customer_id
    LEFT JOIN items on orders.item_id = items.item_id;
```

Query the new stream:

```sql
SELECT *
FROM orders_enriched
EMIT CHANGES;
```

The query output should look like this:

```plaintext
+-----------------+-----------------+-----------------+-----------------+-----------------+-----------------+
|ITEMS_ITEM_ID    |CUSTOMER_ID      |CUSTOMER_NAME    |ORDER_ID         |PURCHASE_DATE    |ITEM_NAME        |
+-----------------+-----------------+-----------------+-----------------+-----------------+-----------------+
|101              |1                |Adrian Garcia    |abc123           |2024-09-01       |Television 60-in |
|102              |1                |Adrian Garcia    |abc345           |2024-09-01       |Laptop 15-in     |
|101              |2                |Robert Miller    |abc678           |2024-09-01       |Television 60-in |
|101              |3                |Brian Smith      |abc987           |2024-09-03       |Television 60-in |
|103              |2                |Robert Miller    |xyz123           |2024-09-03       |Speakers         |
|102              |2                |Robert Miller    |xyz987           |2024-09-05       |Laptop 15-in     |
+-----------------+-----------------+-----------------+-----------------+-----------------+-----------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
