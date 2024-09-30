<!-- title: How to flatten nested JSON with ksqlDB -->
<!-- description: In this tutorial, learn how to flatten nested JSON with ksqlDB, with step-by-step instructions and supporting code. -->

# How to flatten nested JSON with ksqlDB

Consider a topic containing product orders. Each order contains data about the customer and the product, specified as nested data. In this tutorial, we'll write a program that transforms each order into a new version that contains all the data as flat fields.

## Setup

You have JSON data in a topic that has the following structure:
```json
{
  "id": "1",
  "timestamp": "2020-01-18 01:12:05",
  "amount": 84.02,
  "customer": {
    "first_name": "Roberto",
    "last_name": "Smithe",
    "phone_number": "1234567899",
    "address": {
      "street": "street SDF",
      "number": "8602",
      "zipcode": "27640",
      "city": "Raleigh",
      "state": "NC"
    }
  },
  "product": {
    "sku": "P12345",
    "name": "Highly Durable Glue",
    "vendor": {
      "vendor_name": "Acme Corp",
      "country": "US"
    }
  }
}
```
The first step to working with this nested JSON is to create a stream over the topic and use the `STRUCT` keyword to define the fields that contain nested data:
```sql
CREATE STREAM orders (
    id VARCHAR,
    timestamp VARCHAR,
    amount DOUBLE,
    customer STRUCT<first_name VARCHAR,
                    last_name VARCHAR,
                    phone_number VARCHAR,
                    address STRUCT<street VARCHAR,
                                   number VARCHAR,
                                   zipcode VARCHAR,
                                   city VARCHAR,
                                   state VARCHAR>>,
    product STRUCT<sku VARCHAR,
                   name VARCHAR,
                   vendor STRUCT<vendor_name VARCHAR,
                                 country VARCHAR>>)
    WITH (KAFKA_TOPIC='orders',
          VALUE_FORMAT='JSON',
          TIMESTAMP='TIMESTAMP',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
          PARTITIONS=1);
```
     
Next, create a stream that will extract the nested fields into a flat structure:

```sql
CREATE STREAM flattened_orders AS
    SELECT
        id AS order_id,
        timestamp AS order_ts,
        amount AS order_amount,
        customer->first_name AS cust_first_name,
        customer->last_name AS cust_last_name,
        customer->phone_number AS cust_phone_number,
        customer->address->street AS cust_addr_street,
        customer->address->number AS cust_addr_number,
        customer->address->zipcode AS cust_addr_zipcode,
        customer->address->city AS cust_addr_city,
        customer->address->state AS cust_addr_state,
        product->sku AS prod_sku,
        product->name AS prod_name,
        product->vendor->vendor_name AS prod_vendor_name,
        product->vendor->country AS prod_vendor_country
    FROM
        orders;
```

Notice the pattern of `STRUCT->STRUCT->FIELD` to drill down to the nested fields.
 
Now when you want to run query selecting certain attributes of an order you can use much simpler queries:

```sql
SELECT
    order_id,
    order_ts,
    order_amount,
    cust_first_name,
    cust_last_name,
    prod_name
FROM flattened_orders
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

  Run the following SQL statements to create the `orders` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM orders (
      id VARCHAR,
      timestamp VARCHAR,
      amount DOUBLE,
      customer STRUCT<first_name VARCHAR,
                      last_name VARCHAR,
                      phone_number VARCHAR,
                      address STRUCT<street VARCHAR,
                                     number VARCHAR,
                                     zipcode VARCHAR,
                                     city VARCHAR,
                                     state VARCHAR>>,
      product STRUCT<sku VARCHAR,
                     name VARCHAR,
                     vendor STRUCT<vendor_name VARCHAR,
                                   country VARCHAR>>)
      WITH (KAFKA_TOPIC='orders',
            VALUE_FORMAT='JSON',
            TIMESTAMP='TIMESTAMP',
            TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
            PARTITIONS=1);
  ```

  ```sql
  INSERT INTO orders (id, timestamp, amount, customer, product)
      VALUES ('1', '2024-01-18 01:12:05', 89.99,
      STRUCT(first_name := 'Bob',
             last_name := 'Smith',
             address := STRUCT(street := 'Main',
                               number := '12',
                               zipcode := '01020',
                               city := 'Springfield',
                               state := 'MA')),
      STRUCT(sku := '87923',
             name := 'deck of cards',
             vendor := STRUCT(vendor_name := 'Best Brands',
                               country := 'US')));

  INSERT INTO orders (id, timestamp, amount, customer, product)
      VALUES ('2', '2024-01-18 01:12:05', 89.99,
      STRUCT(first_name := 'Jane',
             last_name := 'Jackson',
             address := STRUCT(street := 'Conservation Way',
                               number := '81',
                               zipcode := '01020',
                               city := 'Springfield',
                               state := 'MA')),
      STRUCT(sku := '3992',
             name := 'dog leash',
             vendor := STRUCT(vendor_name := 'Petz',
                               country := 'US')));
  ```

  Next, create a stream that will extract the nested fields into a flat structure. Note that we first tell ksqlDB to 
  consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM flattened_orders AS
      SELECT
          id AS order_id,
          timestamp AS order_ts,
          amount AS order_amount,
          customer->first_name AS cust_first_name,
          customer->last_name AS cust_last_name,
          customer->phone_number AS cust_phone_number,
          customer->address->street AS cust_addr_street,
          customer->address->number AS cust_addr_number,
          customer->address->zipcode AS cust_addr_zipcode,
          customer->address->city AS cust_addr_city,
          customer->address->state AS cust_addr_state,
          product->sku AS prod_sku,
          product->name AS prod_name,
          product->vendor->vendor_name AS prod_vendor_name,
          product->vendor->country AS prod_vendor_country
      FROM
          orders;
  ```

  Now query certain flattened attributes of the orders:

  ```sql
  SELECT
      order_id,
      order_ts,
      order_amount,
      cust_first_name,
      cust_last_name,
      prod_name
  FROM flattened_orders
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +---------------------+---------------------+---------------------+---------------------+---------------------+---------------------+
  |ORDER_ID             |ORDER_TS             |ORDER_AMOUNT         |CUST_FIRST_NAME      |CUST_LAST_NAME       |PROD_NAME            |
  +---------------------+---------------------+---------------------+---------------------+---------------------+---------------------+
  |1                    |2020-01-18 01:12:05  |89.99                |Bob                  |Smith                |deck of cards        |
  |2                    |2024-01-18 01:12:05  |89.99                |Jane                 |Jackson              |dog leash            |
  +---------------------+---------------------+---------------------+---------------------+---------------------+---------------------+
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

  Enter the following statements in the editor and click `Run query`. This creates the `orders` stream and
  populates it with test data.

  ```sql
  CREATE STREAM orders (
      id VARCHAR,
      timestamp VARCHAR,
      amount DOUBLE,
      customer STRUCT<first_name VARCHAR,
                      last_name VARCHAR,
                      phone_number VARCHAR,
                      address STRUCT<street VARCHAR,
                                     number VARCHAR,
                                     zipcode VARCHAR,
                                     city VARCHAR,
                                     state VARCHAR>>,
      product STRUCT<sku VARCHAR,
                     name VARCHAR,
                     vendor STRUCT<vendor_name VARCHAR,
                                   country VARCHAR>>)
      WITH (KAFKA_TOPIC='orders',
            VALUE_FORMAT='JSON',
            TIMESTAMP='TIMESTAMP',
            TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
            PARTITIONS=1);

  INSERT INTO orders (id, timestamp, amount, customer, product)
      VALUES ('1', '2024-01-18 01:12:05', 89.99,
      STRUCT(first_name := 'Bob',
             last_name := 'Smith',
             address := STRUCT(street := 'Main',
                               number := '12',
                               zipcode := '01020',
                               city := 'Springfield',
                               state := 'MA')),
      STRUCT(sku := '87923',
             name := 'deck of cards',
             vendor := STRUCT(vendor_name := 'Best Brands',
                               country := 'US')));

  INSERT INTO orders (id, timestamp, amount, customer, product)
      VALUES ('2', '2024-01-18 01:12:05', 89.99,
      STRUCT(first_name := 'Jane',
             last_name := 'Jackson',
             address := STRUCT(street := 'Conservation Way',
                               number := '81',
                               zipcode := '01020',
                               city := 'Springfield',
                               state := 'MA')),
      STRUCT(sku := '3992',
             name := 'dog leash',
             vendor := STRUCT(vendor_name := 'Petz',
                               country := 'US')));
  ```

  Next, create a stream that will extract the nested fields into a flat structure.

  ```sql
  CREATE STREAM flattened_orders AS
      SELECT
          id AS order_id,
          timestamp AS order_ts,
          amount AS order_amount,
          customer->first_name AS cust_first_name,
          customer->last_name AS cust_last_name,
          customer->phone_number AS cust_phone_number,
          customer->address->street AS cust_addr_street,
          customer->address->number AS cust_addr_number,
          customer->address->zipcode AS cust_addr_zipcode,
          customer->address->city AS cust_addr_city,
          customer->address->state AS cust_addr_state,
          product->sku AS prod_sku,
          product->name AS prod_name,
          product->vendor->vendor_name AS prod_vendor_name,
          product->vendor->country AS prod_vendor_country
      FROM
          orders;
  ```

  Now query certain flattened attributes of the orders:

  ```sql
  SELECT
      order_id,
      order_ts,
      order_amount,
      cust_first_name,
      cust_last_name,
      prod_name
  FROM flattened_orders
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +---------------------+---------------------+---------------------+---------------------+---------------------+---------------------+
  |ORDER_ID             |ORDER_TS             |ORDER_AMOUNT         |CUST_FIRST_NAME      |CUST_LAST_NAME       |PROD_NAME            |
  +---------------------+---------------------+---------------------+---------------------+---------------------+---------------------+
  |1                    |2020-01-18 01:12:05  |89.99                |Bob                  |Smith                |deck of cards        |
  |2                    |2024-01-18 01:12:05  |89.99                |Jane                 |Jackson              |dog leash            |
  +---------------------+---------------------+---------------------+---------------------+---------------------+---------------------+
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
