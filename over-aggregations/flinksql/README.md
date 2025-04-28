<!-- title: How to perform OVER Aggregations in Flink SQL -->
<!-- description: In this tutorial, learn how to use Flink SQL's OVER aggregation to compute an aggregated value for every row over a range of ordered rows, with step-by-step instructions and supporting code. -->

# Perform an aggregation for all rows

When you perform an aggregation in SQL, generally the query reduces the number of result rows to one for every group specified in the `GROUP BY`.  In contrast, [OVER aggregates](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/dev/table/sql/queries/over-agg/#over-aggregation) do not reduce the number of result rows to a single row for every group; instead, they produce an aggregated value for every input row.  OVER aggregations also serve as the basis for more advanced queries like [Top-N](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/dev/table/sql/queries/topn/#top-n), [Window Top-N](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/dev/table/sql/queries/window-topn/#window-top-n), and [Deduplication](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/dev/table/sql/queries/deduplication/#deduplication). 

## Setup

Let's assume the following DDL for a `movie_views` table representing streaming movie service subscriber views:

```sql
TABLE movie_views (
        id INT,
        title STRING,
        genre STRING,
        movie_start TIMESTAMP(3),
        WATERMARK FOR movie_start as movie_start
)
```

## How to use OVER Aggregations

Let's say you work for a movie streaming service and you'd like to count the number of views per genre that are started within 1 hour of each other.  This type of query is easily achieved using an OVER aggregation.

```sql
SELECT title, genre, movie_start, COUNT(*)
OVER (
    PARTITION BY genre
    ORDER BY movie_start
    RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND CURRENT ROW
    ) AS genre_count
FROM movie_views;
```

The `OVER` clause specifies how your aggregation function will operate.  Let's discuss the key point of this SQL statement:

```sql
OVER (
    PARTITION BY genre
    ORDER BY movie_start
    RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND CURRENT ROW
) AS genre_count
```

- `PARTITION BY` is how the query will group the data for the aggregation.  It's not required, if you left the `PARTITION BY` out, then the query would run the aggregation against the full table.
- `ORDER BY` is the time attribute Flink uses to order the results and is required.
- `RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND CURRENT ROW` Is a `RANGE` interval determines the number of rows to include from the current row.  There is a `ROW` interval available which uses a count to determine how many rows to include in the aggregate.  For example to include the 15 previous rows from the current one you'd use `ROWS BETWEEN 15 PRECEDING AND CURRENT ROW`. 

## Running the example

You can run the example backing this tutorial in one of three ways: a Flink Table API-based JUnit test, locally with the Flink SQL Client
against Flink and Kafka running in Docker, or with Confluent Cloud.

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

  Run the following command to execute [FlinkSqlOverAggregationTest#testTopN](https://github.com/confluentinc/tutorials/blob/master/over-aggregations/flinksql/src/test/java/io/confluent/developer/FlinkSqlOverAggregationTest.java):

  ```plaintext
  ./gradlew clean :over-aggregations:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that the aggregation results are what we expect.
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

  Finally, run following SQL statements to create the `movie_views` table backed by Kafka running in Docker, populate it with
  test data, and run the OVER aggregation query.

  ```sql
  CREATE TABLE movie_views (
            id INT,
            title STRING,
            genre STRING,
            movie_start TIMESTAMP(3),
            WATERMARK FOR movie_start as movie_start
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'movie_views',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'id',
      'value.format' = 'json',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  ```sql
  INSERT INTO movie_views VALUES 
         (123, 'The Dark Knight', 'Action', TO_TIMESTAMP('2024-04-23 19:04:00')),
         (456, 'Avengers: Endgame', 'Action', TO_TIMESTAMP('2024-04-23 22:01:00')),
         (789, 'Inception', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 20:24:00')),
         (147, 'Joker', 'Drama', TO_TIMESTAMP('2024-04-23 22:56:00')),
         (258, 'The Godfather', 'Crime', TO_TIMESTAMP('2024-04-23 19:13:00')),
         (369, 'Casablanca', 'Romance', TO_TIMESTAMP('2024-04-23 20:26:00')),
         (321, 'The Shawshank Redemption', 'Drama', TO_TIMESTAMP('2024-04-23 20:20:00')),
         (654, 'Forrest Gump', 'Drama', TO_TIMESTAMP('2024-04-23 21:54:00')),
         (987, 'Fight Club', 'Drama', TO_TIMESTAMP('2024-04-23 23:24:00')),
         (135, 'Pulp Fiction', 'Crime', TO_TIMESTAMP('2024-04-23 22:09:00')),
         (246, 'The Godfather: Part II', 'Crime', TO_TIMESTAMP('2024-04-23 19:28:00')),
         (357, 'The Departed', 'Crime', TO_TIMESTAMP('2024-04-23 23:11:00')),
         (842, 'Toy Story 3', 'Animation', TO_TIMESTAMP('2024-04-23 23:12:00')),
         (931, 'Up', 'Animation', TO_TIMESTAMP('2024-04-23 22:17:00')),
         (624, 'The Lion King', 'Animation', TO_TIMESTAMP('2024-04-23 22:28:00')),
         (512, 'Star Wars: The Force Awakens', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 20:42:00')),
         (678, 'The Matrix', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 19:25:00')),
         (753, 'Interstellar', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 20:14:00')),
         (834, 'Titanic', 'Romance', TO_TIMESTAMP('2024-04-23 20:25:00')),
         (675, 'Pride and Prejudice', 'Romance', TO_TIMESTAMP('2024-04-23 23:37:00')),
         (333, 'The Pride of Archbishop Carroll', 'History', TO_TIMESTAMP('2024-04-24 03:37:00'));
  ```

  ```sql
  SELECT title, genre, movie_start, COUNT(*)
    OVER (
      PARTITION BY genre
      ORDER BY movie_start
      RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND CURRENT ROW
      ) AS genre_count
  FROM movie_views;
  ```

  The query output should look like this:

  ```plaintext
                          title                          genre                   movie_start          genre_count
                The Dark Knight                         Action       2024-04-23 19:04:00.000                    1
                  The Godfather                          Crime       2024-04-23 19:13:00.000                    1
                     The Matrix                         Sci-Fi       2024-04-23 19:25:00.000                    1
         The Godfather: Part II                          Crime       2024-04-23 19:28:00.000                    2
                   Interstellar                         Sci-Fi       2024-04-23 20:14:00.000                    2
       The Shawshank Redemption                          Drama       2024-04-23 20:20:00.000                    1
                      Inception                         Sci-Fi       2024-04-23 20:24:00.000                    3
                        Titanic                        Romance       2024-04-23 20:25:00.000                    1
                     Casablanca                        Romance       2024-04-23 20:26:00.000                    2
   Star Wars: The Force Awakens                         Sci-Fi       2024-04-23 20:42:00.000                    3
                   Forrest Gump                          Drama       2024-04-23 21:54:00.000                    1
              Avengers: Endgame                         Action       2024-04-23 22:01:00.000                    1
                   Pulp Fiction                          Crime       2024-04-23 22:09:00.000                    1
                             Up                      Animation       2024-04-23 22:17:00.000                    1
                  The Lion King                      Animation       2024-04-23 22:28:00.000                    2
                          Joker                          Drama       2024-04-23 22:56:00.000                    1
                   The Departed                          Crime       2024-04-23 23:11:00.000                    1
                    Toy Story 3                      Animation       2024-04-23 23:12:00.000                    3
                     Fight Club                          Drama       2024-04-23 23:24:00.000                    2
            Pride and Prejudice                        Romance       2024-04-23 23:37:00.000                    1
  The Pride of Archbishop Carro~                       History       2024-04-24 03:37:00.000                    1
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

  In the Confluent Cloud Console, navigate to your environment and then click the `Open SQL Workspace` button for the compute
  pool that you have created.

  Select the default catalog (Confluent Cloud environment) and database (Kafka cluster) to use with the dropdowns at the top right.

  Finally, run following SQL statements to create the `movie_views` table, populate it with test data, and run the OVER aggregation query.

  ```sql
  CREATE TABLE movie_views (
        id INT,
        title STRING,
        genre STRING,
        movie_start TIMESTAMP(3),
        WATERMARK FOR movie_start as movie_start
  ) DISTRIBUTED BY (id) INTO 1 BUCKETS;
  ```

  ```sql
  INSERT INTO movie_views VALUES     
         (123, 'The Dark Knight', 'Action', TO_TIMESTAMP('2024-04-23 19:04:00')),
         (456, 'Avengers: Endgame', 'Action', TO_TIMESTAMP('2024-04-23 22:01:00')),
         (789, 'Inception', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 20:24:00')),
         (147, 'Joker', 'Drama', TO_TIMESTAMP('2024-04-23 22:56:00')),
         (258, 'The Godfather', 'Crime', TO_TIMESTAMP('2024-04-23 19:13:00')),
         (369, 'Casablanca', 'Romance', TO_TIMESTAMP('2024-04-23 20:26:00')),
         (321, 'The Shawshank Redemption', 'Drama', TO_TIMESTAMP('2024-04-23 20:20:00')),
         (654, 'Forrest Gump', 'Drama', TO_TIMESTAMP('2024-04-23 21:54:00')),
         (987, 'Fight Club', 'Drama', TO_TIMESTAMP('2024-04-23 23:24:00')),
         (135, 'Pulp Fiction', 'Crime', TO_TIMESTAMP('2024-04-23 22:09:00')),
         (246, 'The Godfather: Part II', 'Crime', TO_TIMESTAMP('2024-04-23 19:28:00')),
         (357, 'The Departed', 'Crime', TO_TIMESTAMP('2024-04-23 23:11:00')),
         (842, 'Toy Story 3', 'Animation', TO_TIMESTAMP('2024-04-23 23:12:00')),
         (931, 'Up', 'Animation', TO_TIMESTAMP('2024-04-23 22:17:00')),
         (624, 'The Lion King', 'Animation', TO_TIMESTAMP('2024-04-23 22:28:00')),
         (512, 'Star Wars: The Force Awakens', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 20:42:00')),
         (678, 'The Matrix', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 19:25:00')),
         (753, 'Interstellar', 'Sci-Fi', TO_TIMESTAMP('2024-04-23 20:14:00')),
         (834, 'Titanic', 'Romance', TO_TIMESTAMP('2024-04-23 20:25:00')),
         (675, 'Pride and Prejudice', 'Romance', TO_TIMESTAMP('2024-04-23 23:37:00')),
         (333, 'The Pride of Archbishop Carroll', 'History', TO_TIMESTAMP('2024-04-24 03:37:00'));
  ```

  ```sql
  SELECT title, genre,  movie_start, COUNT(*)
      OVER (
      PARTITION BY genre
      ORDER BY movie_start
      RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND CURRENT ROW
      ) AS genre_count
  FROM movie_views;
  ```

 The query output should look like this:

 ![Query output](https://raw.githubusercontent.com/confluentinc/tutorials/master/over-aggregations/flinksql/img/query-output.png)

</details>
