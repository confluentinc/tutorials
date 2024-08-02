<!-- title: How to count the number of events in a Kafka topic with Flink SQL -->
<!-- description: In this tutorial, learn how to count the number of events in a Kafka topic with Flink SQL, with step-by-step instructions and supporting code. -->

# How to count the number of events in a Kafka topic with Flink SQL

Suppose you have a topic with events that represent ticket sales for movies. In this tutorial, use Flink SQL to
calculate the total number of tickets sold per movie.

## Setup

Let's assume the following DDL for our base `movie_ticket_sales` table:

```sql
CREATE TABLE movie_ticket_sales (
    title STRING,
    sales_ts STRING,
    total_ticket_value INT
)
```

## Compute count aggregation

Given the `movie_ticket_sales` table definition above, we can figure out the total number of tickets sold per movie using
the following `COUNT` aggregation:

```sql
SELECT title,
       COUNT(total_ticket_value) AS tickets_sold
FROM movie_ticket_sales
GROUP BY title;
```

## Running the example

You can run the example backing this tutorial in one of three ways: a Flink Table API-based JUnit test, locally with the Flink SQL Client 
against Flink and Kafka running in Docker, or with Confluent Cloud.

<details>
  <summary>Flink Table API-based test</summary>

  #### Prerequisites

  * Java 17, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. 
  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)

  #### Run the test

Run the following command to execute [FlinkSqlAggregatingCountTest#testCountAggregation](src/test/java/io/confluent/developer/FlinkSqlAggregatingCountTest.java):

  ```plaintext
  ./gradlew clean :aggregating-count:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that the aggregation results are what we expect.
</details>

<details>
  <summary>Flink SQL Client CLI</summary>

  #### Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

  #### Run the commands

  First, start Flink and Kafka:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml up -d
  ```

  Next, open the Flink SQL Client CLI:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Finally, run following SQL statements to create the `movie_ticket_sales` table backed by Kafka running in Docker, populate it with
  test data, and run the aggregating count query.

  ```sql
  CREATE TABLE movie_ticket_sales (
      title STRING,
      sales_ts STRING,
      total_ticket_value INT
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'movie-ticket-sales',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'title',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  ```sql
  INSERT INTO movie_ticket_sales VALUES
      ('Aliens', '2019-07-18T10:00:00Z', 10),
      ('Die Hard', '2019-07-18T10:00:00Z', 12),
      ('Die Hard', '2019-07-18T10:01:00Z', 12),
      ('The Godfather', '2019-07-18T10:01:31Z', 12),
      ('Die Hard', '2019-07-18T10:01:36Z', 24),
      ('The Godfather', '2019-07-18T10:02:00Z', 18),
      ('The Big Lebowski', '2019-07-18T11:03:21Z', 12),
      ('The Big Lebowski', '2019-07-18T11:03:50Z', 12),
      ('The Godfather', '2019-07-18T11:40:00Z', 36),
      ('The Godfather', '2019-07-18T11:40:09Z', 18);
  ```

  ```sql
  SELECT title,
         COUNT(total_ticket_value) AS tickets_sold
  FROM movie_ticket_sales
  GROUP BY title;
  ```

  The query output should look like this:

  ```plaintext
             title tickets_sold
  ---------------- -----------
            Aliens           1
          Die Hard           3
  The Big Lebowski           2
     The Godfather           4
  ```

  When you are finished, clean up the containers used for this tutorial by running:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml down
  ```

</details>

<details>
  <summary>Confluent Cloud</summary>

  #### Prerequisites

  * A [Confluent Cloud](https://confluent.cloud/signup) account
  * A Flink compute pool created in Confluent Cloud. Follow [this](https://docs.confluent.io/cloud/current/flink/get-started/quick-start-cloud-console.html) quick start to create one.

  #### Run the commands

  In the Confluent Cloud Console, navigate to your environment and then click the `Open SQL Workspace` button for the compute
  pool that you have created.

  Select the default catalog (Confluent Cloud environment) and database (Kafka cluster) to use with the dropdowns at the top right.

  Finally, run following SQL statements to create the `movie_ticket_sales` table, populate it with test data, and run the aggregating count query.

  ```sql
  CREATE TABLE movie_ticket_sales (
      title STRING,
      sales_ts STRING,
      total_ticket_value INT
  );
  ```

  ```sql
  INSERT INTO movie_ticket_sales VALUES
      ('Aliens', '2019-07-18T10:00:00Z', 10),
      ('Die Hard', '2019-07-18T10:00:00Z', 12),
      ('Die Hard', '2019-07-18T10:01:00Z', 12),
      ('The Godfather', '2019-07-18T10:01:31Z', 12),
      ('Die Hard', '2019-07-18T10:01:36Z', 24),
      ('The Godfather', '2019-07-18T10:02:00Z', 18),
      ('The Big Lebowski', '2019-07-18T11:03:21Z', 12),
      ('The Big Lebowski', '2019-07-18T11:03:50Z', 12),
      ('The Godfather', '2019-07-18T11:40:00Z', 36),
      ('The Godfather', '2019-07-18T11:40:09Z', 18);
  ```

  ```sql
  SELECT title,
         COUNT(total_ticket_value) AS tickets_sold
  FROM movie_ticket_sales
  GROUP BY title;
  ```

  The query output should look like this:

  ![Query output](https://raw.githubusercontent.com/confluentinc/tutorials/master/aggregating-count/flinksql/img/query-output.png)
