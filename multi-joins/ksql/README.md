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
  will consume from the beginning of the streams we create.

  Enter the following statements in the editor and click `Run query`. This creates the `orders` stream and `customers` 
  and `items` tables and populates them with test data.

  ```sql
  CREATE STREAM orders (order_id STRING KEY, customer_id STRING, item_id STRING, purchase_date STRING)
      WITH (KAFKA_TOPIC='orders',
            VALUE_FORMAT='JSON',
            PARTITIONS=1);

  CREATE TABLE customers (customer_id STRING PRIMARY KEY, customer_name STRING)
      WITH (KAFKA_TOPIC='customers',
            VALUE_FORMAT='JSON',
            PARTITIONS=1);

  CREATE TABLE items (item_id STRING PRIMARY KEY, item_name STRING)
      WITH (KAFKA_TOPIC='items',
            VALUE_FORMAT='JSON',
            PARTITIONS=1);

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

  Now, paste the stream-table-table join query in the editor and click `Run query`. This will land the results in a 
  new `orders_enriched` stream.

  ```sql
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
