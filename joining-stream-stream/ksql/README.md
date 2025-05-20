<!-- title: How to join two streams of data with ksqlDB -->
<!-- description: In this tutorial, learn how to join two streams of data with ksqlDB, with step-by-step instructions and supporting code. -->

# How to join two streams of data with ksqlDB

In this tutorial, we'll demonstrate how to join two event streams on a common key in order to create a new enriched
event stream.

## Setup

Consider you have two streams `orders` and `shipments`.
 
Here's the `orders` stream definition:
```sql
CREATE STREAM orders (id INT KEY, order_ts VARCHAR, total_amount DOUBLE, customer_name VARCHAR)
    WITH (KAFKA_TOPIC='_orders',
          VALUE_FORMAT='JSON',
          TIMESTAMP='order_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=4);
```

This is the `shipments` stream definition:
```sql
CREATE STREAM shipments (id VARCHAR KEY, ship_ts VARCHAR, order_id INT, warehouse VARCHAR)
    WITH (KAFKA_TOPIC='_shipments',
          VALUE_FORMAT='JSON',
          TIMESTAMP='ship_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=4);
```

You might have noticed that we specified 4 partitions for both streams. It’s not random that both streams have the same partition count. For joins to work correctly, the topics need to be co-partitioned, meaning that all topics have the same number of partitions and are keyed the same way. 

Now you'll join these two streams to get more complete information on orders shipped within the last 7 days:

```sql
CREATE STREAM shipped_orders AS
    SELECT o.id AS order_id,
           TIMESTAMPTOSTRING(o.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS order_ts,
           o.total_amount,
           o.customer_name,
           s.id AS shipment_id,
           TIMESTAMPTOSTRING(s.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS shipment_ts,
           s.warehouse,
           (s.rowtime - o.rowtime) / 1000 / 60 AS ship_time
    FROM orders o INNER JOIN shipments s
    WITHIN 7 DAYS
    ON o.id = s.order_id;
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

  Run the following SQL statements to create the `orders` and `shipments` streams backed by Kafka running in Docker and 
  populate them with test data.

  ```sql
  CREATE STREAM orders (id INT KEY, order_ts VARCHAR, total_amount DOUBLE, customer_name VARCHAR)
      WITH (KAFKA_TOPIC='_orders',
            VALUE_FORMAT='JSON',
            TIMESTAMP='order_ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
            PARTITIONS=4);
  ```

  ```sql
  CREATE STREAM shipments (id VARCHAR KEY, ship_ts VARCHAR, order_id INT, warehouse VARCHAR)
      WITH (KAFKA_TOPIC='_shipments',
            VALUE_FORMAT='JSON',
            TIMESTAMP='ship_ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
            PARTITIONS=4);
  ```

  ```sql
  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (1, '2024-09-29T06:01:18Z', 133.84, 'Danica Fine');
  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (2, '2024-09-29T17:02:20Z', 164.31, 'Tim Berglund');
  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (3, '2024-09-29T13:44:10Z', 90.66, 'Sandon Jacobs');
  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (4, '2024-09-29T11:58:25Z', 33.11, 'Viktor Gamov');

  INSERT INTO shipments (id, ship_ts, order_id, warehouse) VALUES ('ship-ch83360', '2024-09-30T18:13:39Z', 1, 'UPS');
  INSERT INTO shipments (id, ship_ts, order_id, warehouse) VALUES ('ship-xf72808', '2024-09-30T02:04:13Z', 2, 'UPS');
  INSERT INTO shipments (id, ship_ts, order_id, warehouse) VALUES ('ship-kr47454', '2024-09-30T20:47:09Z', 3, 'DHL');
  ```

  Finally, run the stream-stream join query and land the results in a new `shipped_orders` stream. Note that we first
  tell ksqlDB to consume from the beginning of the streams.
  
  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM shipped_orders AS
      SELECT o.id AS order_id,
             TIMESTAMPTOSTRING(o.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS order_ts,
             o.total_amount,
             o.customer_name,
             s.id AS shipment_id,
             TIMESTAMPTOSTRING(s.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS shipment_ts,
             s.warehouse,
             (s.rowtime - o.rowtime) / 1000 / 60 AS ship_time
      FROM orders o INNER JOIN shipments s
      WITHIN 7 DAYS GRACE PERIOD 2 SECONDS
      ON o.id = s.order_id
      EMIT CHANGES;
  ```

  Query the new stream:

  ```sql
  SELECT customer_name,
         order_id,
         order_ts,
         shipment_ts,
         ship_time
  FROM shipped_orders
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +---------------------+---------------------+---------------------+---------------------+---------------------+
  |CUSTOMER_NAME        |ORDER_ID             |ORDER_TS             |SHIPMENT_TS          |SHIP_TIME            |
  +---------------------+---------------------+---------------------+---------------------+---------------------+
  |Danica Fine          |1                    |2024-09-29 06:01:18  |2024-09-30 18:13:39  |2172                 |
  |Tim Berglund         |2                    |2024-09-29 17:02:20  |2024-09-30 02:04:13  |541                  |
  |Sandon Jacobs        |3                    |2024-09-29 13:44:10  |2024-09-30 20:47:09  |1862                 |
  +---------------------+---------------------+---------------------+---------------------+---------------------+
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

  Enter the following statements in the editor and click `Run query`. This creates the `orders` and `shipments` streams
  and populates them with test data.

  ```sql
  CREATE STREAM orders (id INT KEY, order_ts VARCHAR, total_amount DOUBLE, customer_name VARCHAR)
      WITH (KAFKA_TOPIC='_orders',
            VALUE_FORMAT='JSON',
            TIMESTAMP='order_ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
            PARTITIONS=4);

  CREATE STREAM shipments (id VARCHAR KEY, ship_ts VARCHAR, order_id INT, warehouse VARCHAR)
      WITH (KAFKA_TOPIC='_shipments',
            VALUE_FORMAT='JSON',
            TIMESTAMP='ship_ts',
            TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
            PARTITIONS=4);

  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (1, '2024-09-29T06:01:18Z', 133.84, 'Danica Fine');
  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (2, '2024-09-29T17:02:20Z', 164.31, 'Tim Berglund');
  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (3, '2024-09-29T13:44:10Z', 90.66, 'Sandon Jacobs');
  INSERT INTO orders (id, order_ts, total_amount, customer_name) VALUES (4, '2024-09-29T11:58:25Z', 33.11, 'Viktor Gamov');

  INSERT INTO shipments (id, ship_ts, order_id, warehouse) VALUES ('ship-ch83360', '2024-09-30T18:13:39Z', 1, 'UPS');
  INSERT INTO shipments (id, ship_ts, order_id, warehouse) VALUES ('ship-xf72808', '2024-09-30T02:04:13Z', 2, 'UPS');
  INSERT INTO shipments (id, ship_ts, order_id, warehouse) VALUES ('ship-kr47454', '2024-09-30T20:47:09Z', 3, 'DHL');
  ```

  Now, paste the stream-stream join query in the editor and click `Run query`. This will land the results in a 
  new `shipped_orders` stream.
  
  ```sql
  CREATE STREAM shipped_orders AS
      SELECT o.id AS order_id,
             TIMESTAMPTOSTRING(o.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS order_ts,
             o.total_amount,
             o.customer_name,
             s.id AS shipment_id,
             TIMESTAMPTOSTRING(s.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS shipment_ts,
             s.warehouse,
             (s.rowtime - o.rowtime) / 1000 / 60 AS ship_time
      FROM orders o INNER JOIN shipments s
      WITHIN 7 DAYS GRACE PERIOD 2 SECONDS
      ON o.id = s.order_id
      EMIT CHANGES;
  ```

  Query the new stream:

  ```sql
  SELECT customer_name,
         order_id,
         order_ts,
         shipment_ts,
         ship_time
  FROM shipped_orders
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +---------------------+---------------------+---------------------+---------------------+---------------------+
  |CUSTOMER_NAME        |ORDER_ID             |ORDER_TS             |SHIPMENT_TS          |SHIP_TIME            |
  +---------------------+---------------------+---------------------+---------------------+---------------------+
  |Danica Fine          |1                    |2024-09-29 06:01:18  |2024-09-30 18:13:39  |2172                 |
  |Tim Berglund         |2                    |2024-09-29 17:02:20  |2024-09-30 02:04:13  |541                  |
  |Sandon Jacobs        |3                    |2024-09-29 13:44:10  |2024-09-30 20:47:09  |1862                 |
  +---------------------+---------------------+---------------------+---------------------+---------------------+
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
