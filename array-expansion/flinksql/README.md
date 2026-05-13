<!-- title: How to expand arrays into rows per element with Flink SQL -->
<!-- description: In this tutorial, learn how to expand arrays into rows per element with Flink SQL, with step-by-step instructions and supporting code. -->

# How to expand arrays into rows per element with Flink SQL

Suppose you have an array defined in a Flink table, i.e., the column is of type [`ARRAY<t>`](https://docs.confluent.io/cloud/current/flink/reference/datatypes.html#flink-sql-array) where `t` is the subtype of elements in the array. In this tutorial, we'll use Flink SQL's `UNNEST` table function on the array column in order to create a temporary table object that can be cross joined to the original table. We'll work with a concrete tourism example, where we have a table of traveler locations that contains an array column of cities visited. We'll demonstrate how to use Flink SQL to expand this into a row per city visited.

## Setup

Let's assume the following DDL for our base `traveler_locations` table, where `cities_visited` is an array containing elements like `'Rome'` and `'London'`.

```sql
CREATE TABLE traveler_locations (
    traveler_id INT,
    traveler_name STRING,
    cities_visited ARRAY<STRING>
);
```

## Expand array column into rows per array element

Given the `traveler_locations` table definition above, we can expand each row's `cities_visited` column into a row per city in the array using Flink SQL's `UNNEST` table function. The function returns a set of new rows that can be cross joined to its original row.

```sql
SELECT
    traveler_name,
    city
FROM traveler_locations
CROSS JOIN UNNEST(cities_visited) AS city;
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

  Run the following command to execute [FlinkSqlArrayExpansionTest#testArrayExpansion](https://github.com/confluentinc/tutorials/blob/master/array-expansion/flinksql/src/test/java/io/confluent/developer/FlinkSqlArrayExpansionTest.java):

  ```plaintext
  ./gradlew clean :array-expansion:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that the array expansion query results are what we expect.
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

  Run following SQL statement to create the `traveler_locations` table backed by Kafka running in Docker.

  ```sql
  CREATE TABLE traveler_locations (
      traveler_id INT,
      traveler_name STRING,
      cities_visited ARRAY<STRING>
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'traveler-locations',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'traveler_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  Populate the `traveler_locations` table with test data.

  ```sql
  INSERT INTO traveler_locations VALUES
      (0, 'Jane Smith', ARRAY['Rome', 'Paris']),
      (1, 'Xander Jackson', ARRAY['Berlin', 'Paris']),
      (2, 'Sally Stewart', ARRAY['Lisbon']);
  ```

  Finally, run the array expansion query to yield a row for each city that a given traveler has visited.

  ```sql
  SELECT
      traveler_name,
      city
  FROM traveler_locations
  CROSS JOIN UNNEST(cities_visited) AS city;
  ```

  The query output should look like this:

  ```plaintext
   traveler_name            city
      Jane Smith            Rome
      Jane Smith           Paris
  Xander Jackson          Berlin
  Xander Jackson           Paris
   Sally Stewart          Lisbon
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

  Run following SQL statement to create the `traveler_locations` table.

  ```sql
  CREATE TABLE traveler_locations (
      traveler_id INT,
      traveler_name STRING,
      cities_visited ARRAY<STRING>
  );
  ```

  Populate the `traveler_locations` table with test data.

  ```sql
  INSERT INTO traveler_locations VALUES
      (0, 'Jane Smith', ARRAY['Rome', 'Paris']),
      (1, 'Xander Jackson', ARRAY['Berlin', 'Paris']),
      (2, 'Sally Stewart', ARRAY['Lisbon']);
  ```

  Finally, run the array expansion query to yield a row for each city that a given traveler has visited.

  ```sql
  SELECT
      traveler_name,
      city
  FROM traveler_locations
  CROSS JOIN UNNEST(cities_visited) AS city;
  ```

  The query output should look like this:

  ```plaintext
   traveler_name            city
      Jane Smith            Rome
      Jane Smith           Paris
  Xander Jackson          Berlin
  Xander Jackson           Paris
   Sally Stewart          Lisbon
  ```

</details>
