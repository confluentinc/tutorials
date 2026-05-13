<!-- title: How to fetch prior events by key with Flink SQL -->
<!-- description: In this tutorial, learn how to fetch prior events by key with Flink SQL, with step-by-step instructions and supporting code. -->

# How to fetch lagging events with Flink SQL

Suppose you have time series events in a Kafka topic and wish to read events along with previous events in the stream. For example, let's say you have a topic with events that represent a stream of temperature readings over time. In this tutorial, we'll use Flink SQL's `LAG` window function to detect if a temperature reading is an increase or decrease compared to the previous reading.

## Setup

Let's assume the following DDL for our base `temperature_readings` table. Note that, because we will fetch prior events using an `OVER` window that processes events in order, the table must include a time attribute and `WATERMARK` clause to govern event order.

```sql
CREATE TABLE temperature_readings (
    sensor_id INT,
    temperature DOUBLE,
    ts TIMESTAMP(3),
    -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
    WATERMARK FOR ts AS ts
);
```

## Output events along with values from the prior event

Given the `temperature_readings` table definition above, we can output a sensor's current and previous readings as follows:

```sql
  SELECT sensor_id,
         ts,
         temperature,
         LAG(temperature, 1) OVER (PARTITION BY sensor_id ORDER BY ts) AS previous_temperature
  FROM temperature_readings;
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

  Run the following command to execute [FlinkSqlLaggingEventsTest#testLaggingEvents](https://github.com/confluentinc/tutorials/blob/master/lagging-events/flinksql/src/test/java/io/confluent/developer/FlinkSqlLaggingEventsTest.java):

  ```plaintext
  ./gradlew clean :lagging-events:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that the lagging events query results are what we expect.
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

  Run following SQL statement to create the `temperature_readings` table backed by Kafka running in Docker.

  ```sql
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      ts TIMESTAMP(3),
      -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
      WATERMARK FOR ts AS ts
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'temperature-readings',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'sensor_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  Populate the `temperature_readings` table with test data. Notice that, over time, sensor 0's temperature down, then up, then up, while sensor 1's temperature goes up, then down, then up.

  ```sql
  INSERT INTO temperature_readings VALUES
      (0, 55, TO_TIMESTAMP('2024-11-15 02:15:30')),
      (1, 50, TO_TIMESTAMP('2024-11-15 02:15:30')),
      (0, 45, TO_TIMESTAMP('2024-11-15 02:25:30')),
      (1, 52, TO_TIMESTAMP('2024-11-15 02:25:30')),
      (0, 49, TO_TIMESTAMP('2024-11-15 02:35:30')),
      (1, 50, TO_TIMESTAMP('2024-11-15 02:35:30')),
      (0, 57, TO_TIMESTAMP('2024-11-15 02:45:30')),
      (1, 62, TO_TIMESTAMP('2024-11-15 02:45:30'));
  ```

  Finally, query the sensor temperatures along with the previous reading for each.

  ```sql
  SELECT sensor_id,
         ts,
         temperature,
         LAG(temperature, 1) OVER (PARTITION BY sensor_id ORDER BY ts) AS previous_temperature
  FROM temperature_readings;
  ```

  The query output should look like this:

  ```plaintext
  sensor_id                       ts     temperature   previous_temperature
           0  2024-11-15 02:15:30.000           55.0                 <NULL>
           0  2024-11-15 02:25:30.000           45.0                   55.0
           0  2024-11-15 02:35:30.000           49.0                   45.0
           0  2024-11-15 02:45:30.000           57.0                   49.0
           1  2024-11-15 02:15:30.000           50.0                 <NULL>
           1  2024-11-15 02:25:30.000           52.0                   50.0
           1  2024-11-15 02:35:30.000           50.0                   52.0
           1  2024-11-15 02:45:30.000           62.0                   50.0
  ```

  Note that you would not be able to add a `WHERE` clause that references `previous_temperature` because windowed aggregate expressions aren't allowed directly in `WHERE` clause. You can use a subquery to accomplish this, though. For example, to find temperature increases:

  ```sql
  WITH lagging_temperature_readings AS (
    SELECT sensor_id,
           ts,
           temperature,
           LAG(temperature, 1) OVER (PARTITION BY sensor_id ORDER BY ts) AS previous_temperature
    FROM temperature_readings
  )
  SELECT *
  FROM lagging_temperature_readings
  WHERE previous_temperature IS NOT NULL AND temperature > previous_temperature;
  ```

  The query output should look like this:

  ```plaintext
  sensor_id                       ts     temperature   previous_temperature
           0  2024-11-15 02:35:30.000           49.0                   45.0
           0  2024-11-15 02:45:30.000           57.0                   49.0
           1  2024-11-15 02:25:30.000           52.0                   50.0
           1  2024-11-15 02:45:30.000           62.0                   50.0
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

  Run following SQL statement to create the `temperature_readings` table.

  ```sql
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      ts TIMESTAMP(3),
      -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
      WATERMARK FOR ts AS ts
  );
  ```

  Populate the `temperature_readings` table with test data. Notice that, over time, sensor 0's temperature down, then up, then up, while sensor 1's temperature goes up, then down, then up.

  ```sql
  INSERT INTO temperature_readings VALUES
      (0, 55, TO_TIMESTAMP('2024-11-15 02:15:30')),
      (1, 50, TO_TIMESTAMP('2024-11-15 02:15:30')),
      (0, 45, TO_TIMESTAMP('2024-11-15 02:25:30')),
      (1, 52, TO_TIMESTAMP('2024-11-15 02:25:30')),
      (0, 49, TO_TIMESTAMP('2024-11-15 02:35:30')),
      (1, 50, TO_TIMESTAMP('2024-11-15 02:35:30')),
      (0, 57, TO_TIMESTAMP('2024-11-15 02:45:30')),
      (1, 62, TO_TIMESTAMP('2024-11-15 02:45:30'));
  ```

  Finally, query the sensor temperatures along with the previous reading for each.

  ```sql
  SELECT sensor_id,
         ts,
         temperature,
         LAG(temperature, 1) OVER (PARTITION BY sensor_id ORDER BY ts) AS previous_temperature
  FROM temperature_readings;
  ```

  The query output should look like this:

  ```plaintext
  sensor_id                       ts     temperature   previous_temperature
           0  2024-11-15 02:15:30.000           55.0                 <NULL>
           0  2024-11-15 02:25:30.000           45.0                   55.0
           0  2024-11-15 02:35:30.000           49.0                   45.0
           0  2024-11-15 02:45:30.000           57.0                   49.0
           1  2024-11-15 02:15:30.000           50.0                 <NULL>
           1  2024-11-15 02:25:30.000           52.0                   50.0
           1  2024-11-15 02:35:30.000           50.0                   52.0
           1  2024-11-15 02:45:30.000           62.0                   50.0
  ```

  Note that you would not be able to add a `WHERE` clause that references `previous_temperature` because windowed aggregate expressions aren't allowed directly in `WHERE` clause. You can use a subquery to accomplish this, though. For example, to find temperature increases:

  ```sql
  WITH lagging_temperature_readings AS (
    SELECT sensor_id,
           ts,
           temperature,
           LAG(temperature, 1) OVER (PARTITION BY sensor_id ORDER BY ts) AS previous_temperature
    FROM temperature_readings
  )
  SELECT *
  FROM lagging_temperature_readings
  WHERE previous_temperature IS NOT NULL AND temperature > previous_temperature;
  ```

  The query output should look like this:

  ```plaintext
  sensor_id                       ts     temperature   previous_temperature
           0  2024-11-15 02:35:30.000           49.0                   45.0
           0  2024-11-15 02:45:30.000           57.0                   49.0
           1  2024-11-15 02:25:30.000           52.0                   50.0
           1  2024-11-15 02:45:30.000           62.0                   50.0
  ```

</details>
