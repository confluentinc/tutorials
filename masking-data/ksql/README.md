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
  will consume from the beginning of the stream we create.

  Enter the following statements in the editor and click `Run query`. This creates the `purchases` stream and
  populates it with test data.

  ```sql
  CREATE STREAM purchases (order_id INT, customer_name VARCHAR, date_of_birth VARCHAR,
                           product VARCHAR, order_total_usd DOUBLE, town VARCHAR, country VARCHAR)
      WITH (KAFKA_TOPIC='purchases',
            PARTITIONS=1,
            VALUE_FORMAT='JSON');

  INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
      VALUES (1, 'Britney', '02/29/2000', 'Heart Rate Monitor', 119.93, 'Denver', 'USA');
  INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
      VALUES (2, 'Michael', '06/08/1981', 'Foam Roller', 34.95, 'Los Angeles', 'USA');
  INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
      VALUES (3, 'Kimmy', '05/19/1978', 'Hydration Belt', 50.00, 'Tuscan', 'USA');
  INSERT INTO purchases (order_id, customer_name, date_of_birth, product, order_total_usd, town, country)
      VALUES (4, 'Samantha', '08/05/1983', 'Wireless Headphones', 175.93, 'Tulsa', 'USA');
  ```

  Next, create a new stream from the `purchases` stream with PII data masked. Paste this query in the editor and click
  `Run query`.

  ```sql
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
