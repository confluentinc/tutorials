<!-- title: How to filter messages in a Kafka topic with Flink SQL -->
<!-- description: In this tutorial, learn how to filter messages in a Kafka topic with Flink SQL, with step-by-step instructions and supporting code. -->

# How to filter messages in a Kafka topic with Flink SQL

Consider a topic with events that represent book publications. In this tutorial, we'll use Flink SQL to find only the
publications written by a particular author.

## Setup

Let's assume the following DDL for our base `publication_events` table:

```sql
CREATE TABLE publication_events (
    book_id INT,
    author STRING,
    title STRING
);
```

## Filter events

Given the `publication_events` table definition above, we can filter to the publications by a particular author using a `WHERE` clause:

```sql
SELECT *
FROM publication_events
WHERE author = 'George R. R. Martin';
```

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

  Run the following command to execute [FlinkSqlFilteringTest#testFilter](src/test/java/io/confluent/developer/FlinkSqlFilteringTest.java):

  ```plaintext
  ./gradlew clean :filtering:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that the filter results are what we expect.
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

  Finally, run following SQL statements to create the `publication_events` table backed by Kafka running in Docker, populate it with
  test data, and run the filter query.

  ```sql
  CREATE TABLE publication_events (
      book_id INT,
      author STRING,
      title STRING  
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'publication_events',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'book_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  ```sql
  INSERT INTO publication_events VALUES
      (0, 'C.S. Lewis',          'The Silver Chair'),
      (1, 'George R. R. Martin', 'A Song of Ice and Fire'),
      (2, 'C.S. Lewis',          'Perelandra'),
      (3, 'George R. R. Martin', 'Fire & Blood'),
      (4, 'J. R. R. Tolkien',    'The Hobbit'),
      (5, 'J. R. R. Tolkien',    'The Lord of the Rings'),
      (6, 'George R. R. Martin', 'A Dream of Spring'),
      (7, 'J. R. R. Tolkien',    'The Fellowship of the Ring'),
      (8, 'George R. R. Martin', 'The Ice Dragon'),
      (9, 'Mario Puzo',          'The Godfather');
  ```

  ```sql
  SELECT *
  FROM publication_events
  WHERE author = 'George R. R. Martin';
  ```

  The query output should look like this:

  ```plaintext
       book_id                         author                          title
             1            George R. R. Martin         A Song of Ice and Fire
             3            George R. R. Martin                   Fire & Blood
             6            George R. R. Martin              A Dream of Spring
             8            George R. R. Martin                 The Ice Dragon
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

  Finally, run following SQL statements to create the `publication_events` table, populate it with test data, and run the filter query.

  ```sql
  CREATE TABLE publication_events (
      book_id INT,
      author STRING,
      title STRING  
  );
  ```

  ```sql
  INSERT INTO publication_events VALUES
      (0, 'C.S. Lewis',          'The Silver Chair'),
      (1, 'George R. R. Martin', 'A Song of Ice and Fire'),
      (2, 'C.S. Lewis',          'Perelandra'),
      (3, 'George R. R. Martin', 'Fire & Blood'),
      (4, 'J. R. R. Tolkien',    'The Hobbit'),
      (5, 'J. R. R. Tolkien',    'The Lord of the Rings'),
      (6, 'George R. R. Martin', 'A Dream of Spring'),
      (7, 'J. R. R. Tolkien',    'The Fellowship of the Ring'),
      (8, 'George R. R. Martin', 'The Ice Dragon'),
      (9, 'Mario Puzo',          'The Godfather');
  ```

  ```sql
  SELECT *
  FROM publication_events
  WHERE author = 'George R. R. Martin';
  ```

  The query output should look like this:

  ![Query output](https://raw.githubusercontent.com/confluentinc/tutorials/master/filtering/flinksql/img/query-output.png)

<details>
