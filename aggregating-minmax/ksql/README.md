<!-- title: How to compute the minimum or maximum value of a field with ksqlDB -->
<!-- description: In this tutorial, learn how to compute the minimum or maximum value of a field with ksqlDB, with step-by-step instructions and supporting code. -->

# How to compute the minimum or maximum value of a field with ksqlDB

Suppose you have a topic with events that represent ticket sales for movies. In this tutorial, we will use ksqlDB to
compute the highest and lowest film revenues per year.

## Setup

Let's assume the following DDL for our base `movie_sales` stream:


```sql
CREATE STREAM movie_sales (title VARCHAR, release_year INT, total_sales BIGINT)
    WITH (KAFKA_TOPIC='movie-sales',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

## Computing the min / max

Given the `movie_sales` stream definition above, we can figure out the total number of tickets sold per movie using
the following minimum and maximum aggregation:

```sql
SELECT release_year,
       MIN(total_sales) AS min_total_sales,
       MAX(total_sales) AS max_total_sales
FROM movie_sales
GROUP BY release_year
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

  Run the following SQL statements to create the `movie_sales` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM movie_sales (title VARCHAR, release_year INT, total_sales BIGINT)
      WITH (KAFKA_TOPIC='movie-sales',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Twisters', 2024, 369712130);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Deadpool & Wolverine', 2024, 1318259708);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Oppenheimer', 2023, 975579184);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Barbie', 2023, 1445638421);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Avatar: The Way of Water', 2022, 2320250281);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Jurassic World Dominion', 2022, 1001978080);
  ```

  Finally, run the aggregating min / max query. Note that we first tell ksqlDB to consume from the beginning of the stream, and we also configure the query to use caching so that we only get a single output record per key ().

  ```sql
  SET 'auto.offset.reset'='earliest';
  SET 'ksql.streams.cache.max.bytes.buffering' = '10000000';

  SELECT release_year, 
         MIN(total_sales) AS min_total_sales, 
         MAX(total_sales) AS max_total_sales 
  FROM movie_sales
  GROUP BY release_year
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +--------------------------+--------------------------+--------------------------+
  |RELEASE_YEAR              |MIN_TOTAL_SALES           |MAX_TOTAL_SALES           |
  +--------------------------+--------------------------+--------------------------+
  |2024                      |369712130                 |1318259708                |
  |2023                      |975579184                 |1445638421                |
  |2022                      |1001978080                |2320250281                |
  +--------------------------+--------------------------+--------------------------+
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
  `cache.max.bytes.buffering` with value `10000000`. This configures the aggregation to use caching so that we only get
  a single output record per key (release year).

  Enter the following statements in the editor and click `Run query`. This creates the `movie_sales` stream and
  populates it with test data.

  ```sql
  CREATE STREAM movie_sales (title VARCHAR, release_year INT, total_sales BIGINT)
      WITH (KAFKA_TOPIC='movie-sales',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');

  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Twisters', 2024, 369712130);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Deadpool & Wolverine', 2024, 1318259708);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Oppenheimer', 2023, 975579184);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Barbie', 2023, 1445638421);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Avatar: The Way of Water', 2022, 2320250281);
  INSERT INTO movie_sales (title, release_year, total_sales) VALUES ('Jurassic World Dominion', 2022, 1001978080);
  ```

  Now paste the aggregating min / max query in the editor and click `Run query`:

  ```sql
  SELECT release_year, 
         MIN(total_sales) AS min_total_sales, 
         MAX(total_sales) AS max_total_sales 
  FROM movie_sales
  GROUP BY release_year
  EMIT CHANGES;
  ```

  The query output should look like this (order may vary):

  ```plaintext
  +--------------------------+--------------------------+--------------------------+
  |RELEASE_YEAR              |MIN_TOTAL_SALES           |MAX_TOTAL_SALES           |
  +--------------------------+--------------------------+--------------------------+
  |2024                      |369712130                 |1318259708                |
  |2023                      |975579184                 |1445638421                |
  |2022                      |1001978080                |2320250281                |
  +--------------------------+--------------------------+--------------------------+
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
