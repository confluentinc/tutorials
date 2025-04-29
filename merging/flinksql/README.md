<!-- title: How to merge two streams into one with Flink SQL -->
<!-- description: In this tutorial, learn how to merge two streams into one with Flink SQL, with step-by-step instructions and supporting code. -->

# How to merge two streams into one with Flink SQL

Suppose that you have two Kafka topics containing songs of different genres and you wish to merge them into one. In this tutorial, we'll use Flink SQL to accomplish this task. We'll have an input topic containing rock songs, another input topic containing classical songs, and we'll populate an output topic containing all songs.

## Setup

Let's assume the following DDL for our base `rock_songs` and `classical_songs` tables:

```sql
CREATE TABLE rock_songs (
    artist STRING,
    title STRING
);

CREATE TABLE classical_songs (
    artist STRING,
    title STRING
);
```

## Merge streams

Given the table definition above, we can create a merged table, and add a `genre` column, as follows:

```sql
CREATE TABLE all_songs (
    artist STRING,
    title STRING,
    genre STRING
);

INSERT INTO all_songs
    SELECT
        artist,
        title,
        'rock' AS genre
    FROM rock_songs;

INSERT INTO all_songs
    SELECT
        artist,
        title,
        'classical' AS genre
    FROM classical_songs;
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

  Run the following command to execute [FlinkSqlMergeTablesTest#testMerge](https://github.com/confluentinc/tutorials/blob/master/merging/flinksql/src/test/java/io/confluent/developer/FlinkSqlMergeTablesTest.java):

  ```plaintext
  ./gradlew clean :merging:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that the routed results are what we expect.
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

  Finally, run following SQL statements to create the `rock_songs` and `classical_songs` tables backed by Kafka running in Docker, populate them with
  test data, and then create and populate a merged table containing all songs.

  ```sql
  CREATE TABLE rock_songs (
      artist STRING,
      title STRING
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'rock-songs',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'avro-confluent',
      'key.avro-confluent.url' = 'http://schema-registry:8081',
      'key.fields' = 'artist;title',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'ALL'
  );
  ```

  ```sql
  INSERT INTO rock_songs VALUES
      ('Metallica', 'Fade to Black'),
      ('Smashing Pumpkins', 'Today'),
      ('Pink Floyd', 'Another Brick in the Wall'),
      ('Van Halen', 'Jump'),
      ('Led Zeppelin', 'Kashmir');
  ```

  ```sql
  CREATE TABLE classical_songs (
      artist STRING,
      title STRING
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'classical-songs',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'avro-confluent',
      'key.avro-confluent.url' = 'http://schema-registry:8081',
      'key.fields' = 'artist;title',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'ALL'
  );
  ```

  ```sql
  INSERT INTO classical_songs VALUES
      ('Wolfgang Amadeus Mozart', 'The Magic Flute'),
      ('Johann Pachelbel', 'Canon'),
      ('Ludwig van Beethoven', 'Symphony No. 5'),
      ('Edward Elgar', 'Pomp and Circumstance');
  ```

  ```sql
  CREATE TABLE all_songs (
      artist STRING,
      title STRING,
      genre STRING
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'all-songs',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'avro-confluent',
      'key.avro-confluent.url' = 'http://schema-registry:8081',
      'key.fields' = 'artist;title',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'ALL'
  );
  ```

  ```sql
  INSERT INTO all_songs
      SELECT
          artist,
          title,
          'rock' AS genre
      FROM rock_songs;
  ```

  ```sql
  INSERT INTO all_songs
      SELECT
          artist,
          title,
          'classical' AS genre
      FROM classical_songs;
  ```

  ```sql
  SELECT * FROM all_songs;
  ```

  The query output should look like this:

  ```plaintext
                         artist                          title                          genre
                      Metallica                  Fade to Black                           rock
              Smashing Pumpkins                          Today                           rock
                     Pink Floyd      Another Brick in the Wall                           rock
                      Van Halen                           Jump                           rock
                   Led Zeppelin                        Kashmir                           rock
        Wolfgang Amadeus Mozart                The Magic Flute                      classical
               Johann Pachelbel                          Canon                      classical
           Ludwig van Beethoven                 Symphony No. 5                      classical
                   Edward Elgar          Pomp and Circumstance                      classical
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

  Finally, run following SQL statements to create the `rock_songs` and `classical_songs` tables, populate them with test data, and then create and populate a merged table containing all songs.

  ```sql
  CREATE TABLE rock_songs (
      artist STRING,
      title STRING
  );
  ```

  ```sql
  INSERT INTO rock_songs VALUES
      ('Metallica', 'Fade to Black'),
      ('Smashing Pumpkins', 'Today'),
      ('Pink Floyd', 'Another Brick in the Wall'),
      ('Van Halen', 'Jump'),
      ('Led Zeppelin', 'Kashmir');
  ```

  ```sql
  CREATE TABLE classical_songs (
      artist STRING,
      title STRING
  );
  ```

  ```sql
  INSERT INTO classical_songs VALUES
      ('Wolfgang Amadeus Mozart', 'The Magic Flute'),
      ('Johann Pachelbel', 'Canon'),
      ('Ludwig van Beethoven', 'Symphony No. 5'),
      ('Edward Elgar', 'Pomp and Circumstance');
  ```

  ```sql
  CREATE TABLE all_songs (
      artist STRING,
      title STRING,
      genre STRING
  );
  ```

  ```sql
  INSERT INTO all_songs
      SELECT
          artist,
          title,
          'rock' AS genre
      FROM rock_songs;
  ```

  ```sql
  INSERT INTO all_songs
      SELECT
          artist,
          title,
          'classical' AS genre
      FROM classical_songs;
  ```

  Now query the merged table:

  ```sql
  SELECT * FROM all_songs;
  ```

  The scrollable query output should start like this:

  ![Query output](https://raw.githubusercontent.com/confluentinc/tutorials/master/merging/flinksql/img/query-output.png)

</details>
