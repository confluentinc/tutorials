<!-- title: How to aggregate over tumbling windows with ksqlDB -->
<!-- description: In this tutorial, learn how to aggregate over tumbling windows with ksqlDB, with step-by-step instructions and supporting code. -->

# How to aggregate over tumbling windows with ksqlDB

If you have time series events in a Kafka topic, tumbling windows let you group and aggregate them in fixed-size, non-overlapping, contiguous time intervals.

For example, in this tutorial we start with a stream of movie ratings and calculate the number of ratings per movie over 6-hour tumbling windows.

## Setup

First we need to create a stream of ticket sales. This line of ksqlDB DDL creates a stream and its underlying Kafka topic to represent 
a stream of movie ratings. If the topic already exists, then ksqlDB simply registers it as the source of data underlying the new stream. 
The stream has three fields: `title`, the name of the movie; `release_year`, the year the movie was released; `rating`, the rating a viewer gave it; and `timestamp`,
the time at which the rating was made.

```sql
CREATE STREAM ratings (title VARCHAR, release_year INT, rating DOUBLE, timestamp VARCHAR)
    WITH (KAFKA_TOPIC='ratings',
          TIMESTAMP='timestamp',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

## Compute aggregation over tumbling windows

Given the stream of movie ratings, compute the count of ratings per title over 6-hour tumbling windows as follows:

```sql
SELECT title,
       COUNT(*) AS rating_count,
       WINDOWSTART AS window_start,
       WINDOWEND AS window_end
FROM ratings
WINDOW TUMBLING (SIZE 6 HOURS)
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

  Run the following SQL statements to create the `ratings` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM ratings (title VARCHAR, release_year INT, rating DOUBLE, timestamp VARCHAR)
    WITH (KAFKA_TOPIC='ratings',
          TIMESTAMP='timestamp',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Twisters', 2024, 8.2, '2024-09-24 01:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Twisters', 2024, 4.5, '2024-09-24 05:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Twisters', 2024, 5.1, '2024-09-24 07:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Unfrosted', 2024, 4.9, '2024-09-24 09:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Unfrosted', 2024, 5.6, '2024-09-24 08:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Family Switch', 2023, 3.6, '2024-09-24 12:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Family Switch', 2023, 6.0, '2024-09-24 15:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Family Switch', 2023, 4.6, '2024-09-24 22:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Oppenheimer', 2023, 9.9, '2024-09-24 05:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Oppenheimer', 2023, 4.2, '2024-09-24 02:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Inside Out 2', 2024, 3.5, '2024-09-24 18:00:00');
  ```

  Next, run the tumbling window query to generate a table of ratings per title over 6-hour tumbling windows. Note that we
  first tell ksqlDB to consume from the beginning of the stream, and we also configure the query to use caching so that
  we only get one row per tumbling window.

  ```sql
  SET 'auto.offset.reset'='earliest';
  SET 'ksql.streams.cache.max.bytes.buffering' = '10000000';

  SELECT title,
         COUNT(*) AS rating_count,
         WINDOWSTART AS window_start,
         WINDOWEND AS window_end
  FROM ratings
  WINDOW TUMBLING (SIZE 6 HOURS)
  GROUP BY title
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------+-------------------+-------------------+-------------------+
  |TITLE              |RATING_COUNT       |WINDOW_START       |WINDOW_END         |
  +-------------------+-------------------+-------------------+-------------------+
  |Twisters           |2                  |1727136000000      |1727157600000      |
  |Twisters           |1                  |1727157600000      |1727179200000      |
  |Unfrosted          |2                  |1727157600000      |1727179200000      |
  |Family Switch      |2                  |1727179200000      |1727200800000      |
  |Family Switch      |1                  |1727200800000      |1727222400000      |
  |Oppenheimer        |2                  |1727136000000      |1727157600000      |
  |Inside Out 2       |1                  |1727200800000      |1727222400000      |
  +-------------------+-------------------+-------------------+-------------------+
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
  will consume from the beginning of the stream we create. Then click `Add another field` and add a property
  `cache.max.bytes.buffering` with value `10000000`. This configures the tumbling window query to use caching so that we only get
  one row per tumbling window.

  Enter the following statements in the editor and click `Run query`. This creates the `ratings` stream and
  populates it with test data.

  ```sql
  CREATE STREAM ratings (title VARCHAR, release_year INT, rating DOUBLE, timestamp VARCHAR)
    WITH (KAFKA_TOPIC='ratings',
          TIMESTAMP='timestamp',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Twisters', 2024, 8.2, '2024-09-24 01:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Twisters', 2024, 4.5, '2024-09-24 05:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Twisters', 2024, 5.1, '2024-09-24 07:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Unfrosted', 2024, 4.9, '2024-09-24 09:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Unfrosted', 2024, 5.6, '2024-09-24 08:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Family Switch', 2023, 3.6, '2024-09-24 12:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Family Switch', 2023, 6.0, '2024-09-24 15:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Family Switch', 2023, 4.6, '2024-09-24 22:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Oppenheimer', 2023, 9.9, '2024-09-24 05:00:00');
  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Oppenheimer', 2023, 4.2, '2024-09-24 02:00:00');

  INSERT INTO ratings (title, release_year, rating, timestamp) VALUES ('Inside Out 2', 2024, 3.5, '2024-09-24 18:00:00');
  ```

  Next, paste the tumbling window query in the query edit and click `Run query`. This will to generate a table of
  ratings per title over 6-hour tumbling windows

  ```sql
  SELECT title,
         COUNT(*) AS rating_count,
         WINDOWSTART AS window_start,
         WINDOWEND AS window_end
  FROM ratings
  WINDOW TUMBLING (SIZE 6 HOURS)
  GROUP BY title
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------+-------------------+-------------------+-------------------+
  |TITLE              |RATING_COUNT       |WINDOW_START       |WINDOW_END         |
  +-------------------+-------------------+-------------------+-------------------+
  |Twisters           |2                  |1727136000000      |1727157600000      |
  |Twisters           |1                  |1727157600000      |1727179200000      |
  |Unfrosted          |2                  |1727157600000      |1727179200000      |
  |Family Switch      |2                  |1727179200000      |1727200800000      |
  |Family Switch      |1                  |1727200800000      |1727222400000      |
  |Oppenheimer        |2                  |1727136000000      |1727157600000      |
  |Inside Out 2       |1                  |1727200800000      |1727222400000      |
  +-------------------+-------------------+-------------------+-------------------+
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
