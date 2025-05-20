<!-- title: How to compute the sum of a field with ksqlDB -->
<!-- description: In this tutorial, learn how to compute the sum of a field with ksqlDB, with step-by-step instructions and supporting code. -->

# How to compute the sum of a field with ksqlDB

Suppose you have a topic with events that represent ticket sales for movies. In this tutorial, we will use ksqlDB to
calculate the sum of all ticket sales per movie.

## Setup

Let's assume the following DDL for our base `movie_ticket_sales` stream:

```sql
CREATE STREAM movie_ticket_sales (title VARCHAR, sale_ts VARCHAR, ticket_total_value INT)
    WITH (KAFKA_TOPIC='movie-ticket-sales',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

## Computing the sum

Given the `movie_ticket_sales` stream definition above, we can figure out the sum of all ticket sales per movie using
the following `SUM` aggregation:

```sql
SELECT title,
       SUM(ticket_total_value) AS total_value
FROM movie_ticket_sales
GROUP BY title
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

  Run the following SQL statements to create the `movie_ticket_sales` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM movie_ticket_sales (title VARCHAR, sale_ts VARCHAR, ticket_total_value INT)
      WITH (KAFKA_TOPIC='movie-ticket-sales',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Unfrosted', '2024-09-18T10:00:00Z', 10);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Family Switch', '2024-09-18T10:00:00Z', 12);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Family Switch', '2024-09-18T10:01:00Z', 12);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T10:01:31Z', 12);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Family Switch', '2024-09-18T10:01:36Z', 24);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T10:02:00Z', 18);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T11:40:00Z', 36);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T11:40:09Z', 18);
  ```

  Finally, run the aggregating sum query. Note that we first tell ksqlDB to consume from the beginning of the stream, and we also configure the query to use caching so that we only get a single output record per key (movie title).

  ```sql
  SET 'auto.offset.reset'='earliest';
  SET 'ksql.streams.cache.max.bytes.buffering' = '10000000';

  SELECT title,
         SUM(ticket_total_value) AS total_value
  FROM movie_ticket_sales
  GROUP BY title
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +----------------------------------+----------------------------------+
  |TITLE                             |TOTAL_VALUE                       |
  +----------------------------------+----------------------------------+
  |Unfrosted                         |10                                |
  |Family Switch                     |48                                |
  |Twisters                          |84                                |
  +----------------------------------+----------------------------------+
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
  will consume from the beginning of the stream we create. Then click `Add another field` and add a property
  `cache.max.bytes.buffering` with value `10000000`. This configures the sum query to use caching so that we only get
  a single output record per key (movie title).

  Enter the following statements in the editor and click `Run query`. This creates the `movie_ticket_sales` stream and
  populates it with test data.

  ```sql
  CREATE STREAM movie_ticket_sales (title VARCHAR, sale_ts VARCHAR, ticket_total_value INT)
      WITH (KAFKA_TOPIC='movie-ticket-sales',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');

  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Unfrosted', '2024-09-18T10:00:00Z', 10);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Family Switch', '2024-09-18T10:00:00Z', 12);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Family Switch', '2024-09-18T10:01:00Z', 12);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T10:01:31Z', 12);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Family Switch', '2024-09-18T10:01:36Z', 24);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T10:02:00Z', 18);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T11:40:00Z', 36);
  INSERT INTO movie_ticket_sales (title, sale_ts, ticket_total_value) VALUES ('Twisters', '2024-09-18T11:40:09Z', 18);
  ```

  Now paste the sum query in the editor and click `Run query`:

  ```sql
  SELECT title,
         SUM(ticket_total_value) AS total_value
  FROM movie_ticket_sales
  GROUP BY title
  EMIT CHANGES;
  ```

  The query output should look like this (order may vary):

  ```plaintext
  +----------------------------------+----------------------------------+
  |TITLE                             |TOTAL_VALUE                       |
  +----------------------------------+----------------------------------+
  |Unfrosted                         |10                                |
  |Family Switch                     |48                                |
  |Twisters                          |84                                |
  +----------------------------------+----------------------------------+
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
