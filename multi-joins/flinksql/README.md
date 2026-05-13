<!-- title: How to join multiple streams of data with Flink SQL -->
<!-- description: In this tutorial, learn how to join multiple streams of data with Flink SQL, with step-by-step instructions and supporting code. -->

# How to join multiple streams of data with Flink SQL

Suppose you have a fact stream containing orders, as well as related dimension streams of products and customers. In this tutorial, we'll use Flink SQL to join these three streams to create a new, enriched one that contains the product and customer name for each order _as of the time of the order_. This is called a [temporal join](https://docs.confluent.io/cloud/current/flink/reference/queries/joins.html#temporal-joins) because the join results depend on the time relationship of the rows. You may have seen databases refer to this kind of join as a time travel or flashback query.

## Setup

Let's assume the following DDL for our base `orders`, `customers`,  and `products` tables:

```sql
CREATE TABLE orders (
    order_id INT,
    customer_id INT,
    item_id INT,
    quantity INT
);
```

```sql
CREATE TABLE customers (
    customer_id INT,
    name STRING,
    PRIMARY KEY(customer_id) NOT ENFORCED
);
```

```sql
CREATE TABLE products (
    product_id INT,
    name STRING,
    PRIMARY KEY(product_id) NOT ENFORCED
);
```

## Temporal join

Given the `orders`, `customers` and `products` table definitions above, let’s execute a temporal join query to enrich the orders with information about the customer and product at the time of the order.

```sql
SELECT
    orders.order_id AS order_id,
    customers.name AS customer_name,
    products.name AS product_name
FROM orders
    LEFT JOIN customers FOR SYSTEM_TIME AS OF orders.`$rowtime`
        ON orders.customer_id = customers.customer_id
    LEFT JOIN products FOR SYSTEM_TIME AS OF orders.`$rowtime`
        ON orders.item_id = products.product_id;
```

## Running the example

You can run the example backing this tutorial in one of three ways: a Flink Table API-based JUnit test, locally with the Flink SQL Client against Flink and Kafka running in Docker, or with Confluent Cloud.

<details>
  <summary>Flink Table API-based test</summary>

  ### Prerequisites

  * Java 17, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. 
  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

  ### Run the test

  Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

  Run the following command to execute [FlinkSqlMultiJoinTest#testJoin](https://github.com/confluentinc/tutorials/blob/master/multi-joins/flinksql/src/test/java/io/confluent/developer/FlinkSqlMultiJoinTest.java):

  ```plaintext
  ./gradlew clean :multi-joins:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands above against a local Flink `StreamExecutionEnvironment`, and ensures that the join results are what we expect.
</details>

<details>
  <summary>Flink SQL Client CLI</summary>

  ### Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

  ### Run the commands

  Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

  Start Flink and Kafka:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml up -d
  ```

  Next, open the Flink SQL Client CLI:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Run following SQL statements to create the `orders`, `customers`, and `products` tables backed by Kafka running in Docker and populate them with test data. Note that the `customers` and `products` tables use the `upsert-kafka` connector since customers and products get updated over time.

  ```sql
  CREATE TABLE orders (
      ts TIMESTAMP_LTZ(3) METADATA FROM 'timestamp' VIRTUAL,
      order_id INT,
      customer_id INT,
      item_id INT,
      quantity INT,
      WATERMARK FOR ts AS ts
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'orders',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'order_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  ```sql
  CREATE TABLE customers (
      ts TIMESTAMP_LTZ(3) METADATA FROM 'timestamp' VIRTUAL,
      customer_id INT,
      name STRING,
      PRIMARY KEY(customer_id) NOT ENFORCED,
      WATERMARK FOR ts AS ts
  ) WITH (
      'connector' = 'upsert-kafka',
      'topic' = 'customers',
      'properties.bootstrap.servers' = 'broker:9092',
      'key.format' = 'raw',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  ```sql
  CREATE TABLE products (
      ts TIMESTAMP_LTZ(3) METADATA FROM 'timestamp' VIRTUAL,
      product_id INT,
      name STRING,
      PRIMARY KEY(product_id) NOT ENFORCED,
      WATERMARK FOR ts AS ts
  ) WITH (
      'connector' = 'upsert-kafka',
      'topic' = 'products',
      'properties.bootstrap.servers' = 'broker:9092',
      'key.format' = 'raw',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  ```sql
  INSERT INTO customers
  VALUES (1288, 'Phyllis Ackerman'),
         (1827, 'Janis Smithson'),
         (1270, 'William Schnaube');
  ```

  ```sql
  INSERT INTO products
  VALUES ( 9182, 'GripMax Tennis Shoes'),
         ( 9811, 'Air Elite Sneakers');
  ```

  ```sql
  INSERT INTO orders
  VALUES (1, 1288, 9182, 2),
         (2, 1827, 9811, 1),
         (3, 1270, 9182, 3);
  ```

  Next, run the following temporal join query that gives the order ID as well as customer and product name at the time of the order. You will see that this won't return results because Flink needs the watermarks for the `products` and `customers` tables to advance past the order timestamps in order to guarantee deterministic behavior.

  ```sql
  SELECT
      orders.order_id AS order_id,
      customers.name AS customer_name,
      products.name AS product_name
  FROM orders
      LEFT JOIN customers FOR SYSTEM_TIME AS OF orders.ts
         ON orders.customer_id = customers.customer_id
      LEFT JOIN products FOR SYSTEM_TIME AS OF orders.ts
         ON orders.item_id = products.product_id;
  ```

  In order to get results, let's insert a new customer, as well as an update to product `9811` that changes the name from `Air Elite Sneakers` to `Air Elite Basketball Sneakers`.

  ```sql
  INSERT INTO customers
  VALUES (1372, 'Jane Roberts');
  ```

  ```sql
  INSERT INTO products
  VALUES ( 9811, 'Air Elite Basketball Sneakers');
  ```

  Now, rerun the temporal join query and observe the results, including the fact that that order `2` is for `Air Elite Sneakers` since that was the product name as of the time of that order:

  ```plaintext
    order_id                  customer_name                   product_name
           1               Phyllis Ackerman           GripMax Tennis Shoes
           3               William Schnaube           GripMax Tennis Shoes
           2                 Janis Smithson             Air Elite Sneakers
  ```

  When you are finished, clean up the containers used for this tutorial by running:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml down
  ```

</details>

<details>
  <summary>Confluent Cloud</summary>

  ### Prerequisites

  * A [Confluent Cloud](https://confluent.cloud/signup) account
  * A Flink compute pool created in Confluent Cloud. Follow [this](https://docs.confluent.io/cloud/current/flink/get-started/quick-start-cloud-console.html) quick start to create one.

  ### Run the commands

  In the Confluent Cloud Console, navigate to your environment and then click the `Open SQL Workspace` button for the compute pool that you have created.

  Select the default catalog (Confluent Cloud environment) and database (Kafka cluster) to use with the dropdowns at the top right.

  Run following SQL statements to create the `orders`, `customers`, and `products` tables. Note that the latter two are `upsert` tables.

  ```sql
  CREATE TABLE orders (
      order_id INT,
      customer_id INT,
      item_id INT,
      quantity INT
  ) WITH (
      'changelog.mode' = 'append'
  );
  ```

  ```sql
  CREATE TABLE customers (
      customer_id INT,
      name STRING,
      PRIMARY KEY(customer_id) NOT ENFORCED
  ) WITH (
      'changelog.mode' = 'upsert'
  );
  ```

  ```sql
  CREATE TABLE products (
      product_id INT,
      name STRING,
      PRIMARY KEY(product_id) NOT ENFORCED
  ) WITH (
      'changelog.mode' = 'upsert'
  );
  ```

  Modify the watermark strategy for all three tables to be strictly ascending based on the built-in `$rowtime` attribute. This causes watermarks to advance as rows are inserted, which will let us easily see a temporal join in action with a small amount of data (the [default watermark strategy](https://docs.confluent.io/cloud/current/flink/reference/statements/create-table.html#default-watermark-strategy) requires 250 events per partition).

  ```sql
  ALTER TABLE orders
      MODIFY WATERMARK FOR $rowtime AS $rowtime;
  ```

  ```sql
  ALTER TABLE customers
      MODIFY WATERMARK FOR $rowtime AS $rowtime;
  ```

  ```sql
  ALTER TABLE products
      MODIFY WATERMARK FOR $rowtime AS $rowtime;
  ```

  Insert a few customers, products, and orders.

  ```sql
  INSERT INTO customers
  VALUES (1288, 'Phyllis Ackerman'),
         (1827, 'Janis Smithson'),
         (1270, 'William Schnaube');
  ```

  ```sql
  INSERT INTO products
  VALUES ( 9182, 'GripMax Tennis Shoes'),
         ( 9811, 'Air Elite Sneakers');
  ```

  ```sql
  INSERT INTO orders
  VALUES (1, 1288, 9182, 2),
         (2, 1827, 9811, 1),
         (3, 1270, 9182, 3);
  ```

  Next, run the following temporal join query that gives the order ID as well as customer and product name at the time of the order. You will see that this won't return results because Flink needs the watermarks for the `products` and `customers` tables to advance past the order timestamps in order to guarantee deterministic behavior.

  ```sql
  SELECT
      orders.order_id AS order_id,
      customers.name AS customer_name,
      products.name AS product_name
  FROM orders
      LEFT JOIN customers FOR SYSTEM_TIME AS OF orders.`$rowtime`
          ON orders.customer_id = customers.customer_id
      LEFT JOIN products FOR SYSTEM_TIME AS OF orders.`$rowtime`
          ON orders.item_id = products.product_id;
  ```


  In order to get results, let's insert a new customer, as well as an update to product `9811` that changes the name from `Air Elite Sneakers` to `Air Elite Basketball Sneakers`.

  ```sql
  INSERT INTO customers
  VALUES (1372, 'Jane Roberts');
  ```

  ```sql
  INSERT INTO products
  VALUES ( 9811, 'Air Elite Basketball Sneakers');
  ```

  Now, rerun the temporal join query and observe the results, including the fact that that order `2` is for `Air Elite Sneakers` since that was the product name as of the time of that order:

  ```plaintext
    order_id                  customer_name                   product_name
           1               Phyllis Ackerman           GripMax Tennis Shoes
           3               William Schnaube           GripMax Tennis Shoes
           2                 Janis Smithson             Air Elite Sneakers
  ```

  When you are finished, clean up the infrastructure used for this tutorial by deleting the environment that you created in Confluent Cloud

</details>
