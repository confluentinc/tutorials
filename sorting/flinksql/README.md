<!-- title: How to sort events with Flink SQL -->
<!-- description: In this tutorial, learn how to sort events with Flink SQL, with step-by-step instructions and supporting code. -->

# How to sort events with Flink SQL

Suppose you have time series events in a Kafka topic and wish to output the events in sorted order.
For example, let's say you have a topic with events that represent a stream of temperature readings over time. 
In this tutorial, we'll use Flink SQL's [`ORDER BY`](https://docs.confluent.io/cloud/current/flink/reference/queries/orderby.html)
clause to output the readings in sorted order and learn how out-of-order events are handled.

## Setup

Let's assume the following DDL for our `temperature_readings` table:

```sql
CREATE TABLE temperature_readings (
    sensor_id INT,
    temperature DOUBLE,
    ts TIMESTAMP(3),
    -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
    WATERMARK FOR ts AS ts
);
```

The timestamp is an important attribute because sorting events requires that the sort be ascending on a time attribute.

## Sorting events

Given the `temperature_readings` table definition above, we sort events by the event timestamp as follows:

```sql
SELECT *
FROM temperature_readings
ORDER BY ts;
```

The following steps illustrate this with test data and demonstrate how out-of-order events are handled.

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

  Run the following command to execute [FlinkSqlOrderByTest#testOrderBy](https://github.com/confluentinc/tutorials/blob/master/sorting/flinksql/src/test/java/io/confluent/developer/FlinkSqlOrderByTest.java):

  ```plaintext
  ./gradlew clean :sorting:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that `ORDER BY` query results are what we expect.
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

  Finally, run following SQL statements to create the `temperature_readings` table backed by Kafka running in Docker and
  populate it with test data. Note that we also include the Kafka topic partition and offset as virtual columns for illustrative purposes.

  ```sql
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      ts TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
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

  ```sql
  INSERT INTO temperature_readings VALUES
      (0, 55, TO_TIMESTAMP('2024-11-01 02:15:30')),
      (0, 50, TO_TIMESTAMP('2024-11-01 02:20:30')),
      (0, 45, TO_TIMESTAMP('2024-11-01 02:25:30')),
      (0, 40, TO_TIMESTAMP('2024-11-01 02:30:30')),
      (0, 45, TO_TIMESTAMP('2024-11-01 02:35:30')),
      (0, 50, TO_TIMESTAMP('2024-11-01 02:40:30')),
      (0, 55, TO_TIMESTAMP('2024-11-01 02:45:30')),
      (0, 60, TO_TIMESTAMP('2024-11-01 02:50:30')),
      (0, 55, TO_TIMESTAMP('2024-11-01 02:10:30')),
      (0, 60, TO_TIMESTAMP('2024-11-01 02:53:30'));
  ```

  Before running the sort query, observe that the second to last event inserted is out of order (its `ts` field is less
  than that of all other events). We can see that this is reflected in Kafka by observing that it is at offset 8:

  ```sql
  SELECT `partition`,
      `offset`,
      ts
  FROM temperature_readings;
  ```

  The query output should look like this:

  ```plaintext
  partition        offset                       ts
          0             0  2024-11-01 02:15:30.000
          0             1  2024-11-01 02:20:30.000
          0             2  2024-11-01 02:25:30.000
          0             3  2024-11-01 02:30:30.000
          0             4  2024-11-01 02:35:30.000
          0             5  2024-11-01 02:40:30.000
          0             6  2024-11-01 02:45:30.000
          0             7  2024-11-01 02:50:30.000
          0             8  2024-11-01 02:10:30.000
          0             9  2024-11-01 02:53:30.000
  ```

  Now run the sort query:

  ```sql
  SELECT `partition`,
      `offset`,
      ts
  FROM temperature_readings
  ORDER BY ts;
  ```

  Observe that, even though we are using a strictly ascending timestamp watermark strategy, the out-of-order
  event should show up in correct sort order.

  ```plaintext
  partition        offset                       ts
          0             8  2024-11-01 02:10:30.000
          0             0  2024-11-01 02:15:30.000
          0             1  2024-11-01 02:20:30.000
          0             2  2024-11-01 02:25:30.000
          0             3  2024-11-01 02:30:30.000
          0             4  2024-11-01 02:35:30.000
          0             5  2024-11-01 02:40:30.000
          0             6  2024-11-01 02:45:30.000
          0             7  2024-11-01 02:50:30.000
          0             9  2024-11-01 02:53:30.000
  ```

  This happens because Flink emits watermarks periodically (every 200ms), which is very likely enough
  time in this example to run through all ten events. If instead we updated the `temperature_readings` table so that watermarks
  are emitted for every event, then the out-of-order event will be ignored. Let's first update the table to emit watermarks 
  for every event:

  ```sql
  ALTER TABLE temperature_readings SET ('scan.watermark.emit.strategy'='on-event');
  ```

  Now if we rerun the sort query we see that the event at offset 8 does not get output. We can even output the watermark
  for each row by using the built-in [`CURRENT_WATERMARK`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#flink-sql-current-watermark-function)
  function.

  ```sql
  SELECT `partition`,
      `offset`,
      ts,
      CURRENT_WATERMARK(ts) AS `watermark`
  FROM temperature_readings
  ORDER BY ts;
  ```

  We can see that the event at offset 8 is omitted and can observe that the watermark advances for every event.
  By the time the out-of-order event with timestamp `2024-11-01 02:10:30.000` is scanned, the watermark is at `2024-11-01 02:50:30.000`
  so the event is ignored.

  ```plaintext
  partition               offset                       ts                watermark
          0                    0  2024-11-01 02:15:30.000                   <NULL>
          0                    1  2024-11-01 02:20:30.000  2024-11-01 02:15:30.000
          0                    2  2024-11-01 02:25:30.000  2024-11-01 02:20:30.000
          0                    3  2024-11-01 02:30:30.000  2024-11-01 02:25:30.000
          0                    4  2024-11-01 02:35:30.000  2024-11-01 02:30:30.000
          0                    5  2024-11-01 02:40:30.000  2024-11-01 02:35:30.000
          0                    6  2024-11-01 02:45:30.000  2024-11-01 02:40:30.000
          0                    7  2024-11-01 02:50:30.000  2024-11-01 02:45:30.000
          0                    9  2024-11-01 02:53:30.000  2024-11-01 02:50:30.000
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

  Finally, run following SQL statements to create the `temperature_readings` table and populate it with test data.
  Note that we also include the Kafka topic partition and offset for illustrative purposes.

  ```sql
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      ts TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
      -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
      WATERMARK FOR ts AS ts
  );
  ```

  ```sql
  INSERT INTO temperature_readings VALUES
      (0, 55, TO_TIMESTAMP('2024-11-01 02:15:30')),
      (0, 50, TO_TIMESTAMP('2024-11-01 02:20:30')),
      (0, 45, TO_TIMESTAMP('2024-11-01 02:25:30')),
      (0, 40, TO_TIMESTAMP('2024-11-01 02:30:30')),
      (0, 45, TO_TIMESTAMP('2024-11-01 02:35:30')),
      (0, 50, TO_TIMESTAMP('2024-11-01 02:40:30')),
      (0, 55, TO_TIMESTAMP('2024-11-01 02:45:30')),
      (0, 60, TO_TIMESTAMP('2024-11-01 02:50:30')),
      (0, 55, TO_TIMESTAMP('2024-11-01 02:10:30')),
      (0, 60, TO_TIMESTAMP('2024-11-01 02:53:30'));
  ```

  Before running the sort query, observe that the second to last event inserted is out of order (its `ts` field is less
  than that of all other events). We can see that this is reflected in Kafka by observing that it is at offset 8:

  ```sql
  SELECT `partition`,
      `offset`,
      ts
  FROM temperature_readings;
  ```

  The query output should show that the event with timestamp `2024-11-01 02:10:30` is at offse 8.

  Now run the sort query:

  ```sql
  SELECT `partition`,
      `offset`,
      ts
  FROM temperature_readings
  ORDER BY ts;
  ```

  Observe that, even though we are using a strictly ascending timestamp watermark strategy, the out-of-order
  event shows up in correct sort order.

  ```plaintext
  partition        offset                       ts
          0             8  2024-11-01 02:10:30.000
          0             0  2024-11-01 02:15:30.000
          0             1  2024-11-01 02:20:30.000
          0             2  2024-11-01 02:25:30.000
          0             3  2024-11-01 02:30:30.000
          0             4  2024-11-01 02:35:30.000
          0             5  2024-11-01 02:40:30.000
          0             6  2024-11-01 02:45:30.000
          0             7  2024-11-01 02:50:30.000
          0             9  2024-11-01 02:53:30.000
  ```

  This happens because, by default, Flink emits watermarks periodically (every 200ms of wall clock time), which is very
  likely enough time in this example to scan through all ten events before a watermark is emitted.

</details>
