<!-- title: How to pattern match with Flink SQL's `MATCH_RECOGNIZE` -->
<!-- description: In this tutorial, learn how to pattern match with Flink SQL's `MATCH_RECOGNIZE`, with step-by-step instructions and supporting code. -->

# How to pattern match with Flink SQL's `MATCH_RECOGNIZE`

Consider a topic containing temperature readings from device sensors, and imagine you want to detect upward or downward trends in temperature for a given device. This is a use case requiring pattern matching across *multiple* events in a stream. Flink SQL's `MATCH_RECOGNIZE` function is a powerful tool for implementing this kind of streaming [pattern recognition](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql/queries/match_recognize/#pattern-recognition).

## Setup

Let's assume the following DDL for our base `temperature_readings` table:

```sql
CREATE TABLE temperature_readings (
    sensor_id INT,
    temperature DOUBLE,
    ts TIMESTAMP(3),
    -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
    WATERMARK FOR ts AS ts
);
```

## `MATCH_RECOGNIZE` basics

Before we build a query to detect upward or downward trends in temperature for a given sensor, let's first look at the basic components of a `MATCH_RECOGNIZE` query using this example that looks for 3 consecutive events from a given sensor where the temperature is above 52, then below 51, and then above 51 again.

```sql
SELECT *
FROM temperature_readings
    MATCH_RECOGNIZE(
        PARTITION BY sensor_id
        ORDER BY ts ASC
        MEASURES
            A.temperature AS firstTemp,
            A.ts as firstTs,
            B.temperature AS middleTemp,
            B.ts as middleTs,
            C.temperature AS lastTemp,
            C.ts as lastTs
        ONE ROW PER MATCH
        AFTER MATCH SKIP PAST LAST ROW
        PATTERN (A B C)
        DEFINE
            A AS A.temperature > 51,
            B AS B.temperature < 51,
            C AS C.temperature > 51
    ) MR;
```

The majority of the real estate of this query is taken up by the `MATCH_RECOGNIZE` specification. Let's go over its components:

1. [`PARTITION BY`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#partitioning) is like a `GROUP BY` operation in that it specifies the logical groups over which we are searching for event patterns. Because we want to limit our pattern search to individual device sensors, we partition by `sensor_id`.
2. [`ORDER BY`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#order-of-events) specifies how the events should be ordered in each partition. The `ts` timestamp field is a natural choice for this. Together, the ordering and partitioning clauses determine the substreams in which to look for event patterns.
3. [`PATTERN`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#define-a-pattern) and [`DEFINE`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#define-and-measures) specify the sequence of pattern variables to match and the conditions that count as a match. The variables can be quantified in the pattern using [regular expression quantifiers](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#define-a-pattern) like `*` (0 or more occurrences), `+` (1 or more occurrences), and `?` (0 or 1 occurrence). In our case we are looking for 3 consecutive events where the temperature is above 51, then below 51, and then above 51 again, so we define conditions `A`, `B`, and `C` for temperature above, below, and above 51, respectively, and the `PATTERN` is accordingly `A B C`. We could also specify the pattern as `A B A` and get the same match, but we use a separate pattern variable `C` for the end of the pattern so that we can project it unambiguously in the `MEASURES` clause. 
4. [`MEASURES`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#define-and-measures) specifies the columns and expressions to output. In our case we output the timestamp and temperature of each of the three events that make up a pattern match.
5. [`ONE ROW PER MATCH`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#output-mode) is the output mode telling Flink SQL how many rows to emit for a pattern match. Flink SQL only supports `ONE ROW PER MATCH`. Support for `ALL ROWS PER MATCH` may come in the future.
6. [`AFTER MATCH`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#after-match-strategy) specifies where to resume pattern matching after finding a match. `SKIP PAST LAST ROW` resumes *after* the event matching pattern variable `C` in our example. There are other options like `SKIP TO NEXT ROW`, which, in our example, would resume at the row immediately after the first row in the pattern match (the one corresponding to pattern variable `B`).

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

  Run the following command to execute [FlinkSqlPatternMatchingTest#testPatternMatching](https://github.com/confluentinc/tutorials/blob/master/pattern-matching/flinksql/src/test/java/io/confluent/developer/FlinkSqlPatternMatchingTest.java):

  ```plaintext
  ./gradlew clean :pattern-matching:flinksql:test
  ```

  The test starts Kafka and Schema Registry with [Testcontainers](https://testcontainers.com/), runs the Flink SQL commands
  above against a local Flink `StreamExecutionEnvironment`, and ensures that the pattern matching results are what we expect.
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

  Run following SQL statements to create the `temperature_readings` table backed by Kafka running in Docker, and populate it with
  test data.

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

  ```sql
  INSERT INTO temperature_readings VALUES
      (0, 55, TO_TIMESTAMP('2023-04-03 02:00:00')),
      (1, 40, TO_TIMESTAMP('2023-04-03 02:00:01')),
      (2, 59, TO_TIMESTAMP('2023-04-03 02:00:02')),
      (0, 50, TO_TIMESTAMP('2023-04-03 02:00:03')),
      (1, 42, TO_TIMESTAMP('2023-04-03 02:00:04')),
      (2, 57, TO_TIMESTAMP('2023-04-03 02:00:05')),
      (0, 52, TO_TIMESTAMP('2023-04-03 02:00:06')),
      (1, 43, TO_TIMESTAMP('2023-04-03 02:00:07')),
      (2, 56, TO_TIMESTAMP('2023-04-03 02:00:08')),
      (0, 49, TO_TIMESTAMP('2023-04-03 02:00:09')),
      (1, 45, TO_TIMESTAMP('2023-04-03 02:00:10')),
      (2, 55, TO_TIMESTAMP('2023-04-03 02:00:11')),
      (0, 53, TO_TIMESTAMP('2023-04-03 02:00:12')),
      (1, 47, TO_TIMESTAMP('2023-04-03 02:00:13')),
      (2, 53, TO_TIMESTAMP('2023-04-03 02:00:14'));
  ```

  This `INSERT` statement generates temperature readings for 3 sensors (5 readings per sensor). Sensor 0's temperature fluctuates, Sensor 1's temperatures are monotonically increasing, and Sensor 2's are monotonically decreasing.

  | Sensor | Temp 1 | Temp 2 | Temp 3 | Temp 4 | Temp 5 |
  |--------|--------|--------|--------|--------|--------|
  | 0      | 55     | 50     | 52     | 49     | 53     |
  | 1      | 40     | 42     | 43     | 45     | 47     |
  | 2      | 59     | 57     | 56     | 55     | 53     |

  Now, run the example query from above to find any case where three readings for a given sensor are above 51, then below 51, and then above 51 again:
  
  ```sql
  SELECT *
  FROM temperature_readings
      MATCH_RECOGNIZE(
          PARTITION BY sensor_id
          ORDER BY ts ASC
          MEASURES
              A.temperature AS firstTemp,
              A.ts as firstTs,
              B.temperature AS middleTemp,
              B.ts as middleTs,
              C.temperature AS lastTemp,
              C.ts as lastTs
          ONE ROW PER MATCH
          AFTER MATCH SKIP PAST LAST ROW
          PATTERN (A B C)
          DEFINE
              A AS A.temperature > 51,
              B AS B.temperature < 51,
              C AS C.temperature > 51
      ) MR;
  ```
  Observe that Sensor 0's first three readings (55, 50, 52) are the only match. Why aren't the last three readings (52, 49, 53) also a match? Recall that the `AFTER MATCH` strategy of skipping past the last row will resume *after* the reading of 52, which is too far along to recognize the (52, 49, 53) sequence. If you run the same query again but substitute the after match strategy `AFTER MATCH SKIP TO NEXT ROW`, then this second sequence would be returned because the pattern searching would resume at the second reading for Sensor 0 instead of the fourth.

  Now let's run a more interesting pattern matching query to find cases where the temperature at a sensor has increased for 5 consecutive readings. To do this, we use a quantifier `{5}` in our pattern, and the pattern variable itself uses the [`LAST`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#logical-offsets) logical offset operator in order to compare the temperature to that of the previous matching event. We must also include the condition `LAST(TEMP_UP.temperature, 1) IS NULL` to handle the first potential event in the pattern of 5 events that we're looking for. Putting it all together, the following query will find Sensor 1's 5 consecutive temperature increases (40, 42, 43, 45, 47). In the `MATCHES` clause we only output the first and last timestamp and temperature readings.

  ```sql
  SELECT *
  FROM temperature_readings
      MATCH_RECOGNIZE(
          PARTITION BY sensor_id
          ORDER BY ts ASC
          MEASURES
              FIRST(TEMP_UP.ts) AS firstTs,
              FIRST(TEMP_UP.temperature) AS firstTemp,
              LAST(TEMP_UP.ts) AS lastTs,
              LAST(TEMP_UP.temperature) AS lastTemp
          ONE ROW PER MATCH
          AFTER MATCH SKIP PAST LAST ROW
          PATTERN (TEMP_UP{5})
          DEFINE
            TEMP_UP AS
                LAST(TEMP_UP.temperature, 1) IS NULL OR TEMP_UP.temperature > LAST(TEMP_UP.temperature, 1)
      ) MR;
  ```

  As a final step, let's now find sequences of readings that are *either* all increasing or all decreasing. The `PATTERN` component of `MATCH_RECOGNIZE` doesn't support Boolean logic, so, to accomplish this, you can either use a `UNION` of two queries, or use one query that explicitly spells out 5 increasing or decreasing temperatures using the `LAST` logical offset operator.
  
  Here's what the `UNION` approach would look like:

  ```sql
  (SELECT *
   FROM temperature_readings
       MATCH_RECOGNIZE(
           PARTITION BY sensor_id
           ORDER BY ts ASC
           MEASURES
               FIRST(TEMP_UP.ts) AS firstTs,
               FIRST(TEMP_UP.temperature) AS firstTemp,
               LAST(TEMP_UP.ts) AS lastTs,
               LAST(TEMP_UP.temperature) AS lastTemp
           ONE ROW PER MATCH
           AFTER MATCH SKIP PAST LAST ROW
           PATTERN (TEMP_UP{5})
           DEFINE
               TEMP_UP AS
                   LAST(TEMP_UP.temperature, 1) IS NULL OR TEMP_UP.temperature > LAST(TEMP_UP.temperature, 1)
       ) MR)
  UNION
  (SELECT *
   FROM temperature_readings
       MATCH_RECOGNIZE(
           PARTITION BY sensor_id
           ORDER BY ts ASC
           MEASURES
               FIRST(TEMP_DOWN.ts) AS firstTs,
               FIRST(TEMP_DOWN.temperature) AS firstTemp,
               LAST(TEMP_DOWN.ts) AS lastTs,
               LAST(TEMP_DOWN.temperature) AS lastTemp
           ONE ROW PER MATCH
           AFTER MATCH SKIP PAST LAST ROW
           PATTERN (TEMP_DOWN{5})
           DEFINE
               TEMP_DOWN AS
                   LAST(TEMP_DOWN.temperature, 1) IS NULL OR TEMP_DOWN.temperature < LAST(TEMP_DOWN.temperature, 1)
       ) MR);
  ```

  Observe that results for both Sensors 1 and 2 are returned.

  The second approach to this, where we explicitly spell out the sequence of 5 increases or decreases in the pattern variable definition, looks like this. Note that, to handle first few events in the pattern we are looking for, we need to check `LAST(TEMP_SAME_DIRECTION.temperature, <offset>)` for `NULL`.

  ```sql
  SELECT *
  FROM temperature_readings
      MATCH_RECOGNIZE(
          PARTITION BY sensor_id
          ORDER BY ts ASC
          MEASURES
              FIRST(TEMP_SAME_DIRECTION.ts) AS firstTs,
              FIRST(TEMP_SAME_DIRECTION.temperature) AS firstTemp,
              LAST(TEMP_SAME_DIRECTION.ts) AS lastTs,
              LAST(TEMP_SAME_DIRECTION.temperature) AS lastTemp
          ONE ROW PER MATCH
          AFTER MATCH SKIP PAST LAST ROW
          PATTERN (TEMP_SAME_DIRECTION{5})
          DEFINE
            TEMP_SAME_DIRECTION AS
                (LAST(TEMP_SAME_DIRECTION.temperature, 1) IS NULL OR TEMP_SAME_DIRECTION.temperature > LAST(TEMP_SAME_DIRECTION.temperature, 1))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 2) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 1) > LAST(TEMP_SAME_DIRECTION.temperature, 2))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 3) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 2) > LAST(TEMP_SAME_DIRECTION.temperature, 3))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 4) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 3) > LAST(TEMP_SAME_DIRECTION.temperature, 4))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 5) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 4) > LAST(TEMP_SAME_DIRECTION.temperature, 5))
                OR
                (LAST(TEMP_SAME_DIRECTION.temperature, 1) IS NULL OR TEMP_SAME_DIRECTION.temperature < LAST(TEMP_SAME_DIRECTION.temperature, 1))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 2) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 1) < LAST(TEMP_SAME_DIRECTION.temperature, 2))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 3) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 2) < LAST(TEMP_SAME_DIRECTION.temperature, 3))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 4) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 3) < LAST(TEMP_SAME_DIRECTION.temperature, 4))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 5) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 4) < LAST(TEMP_SAME_DIRECTION.temperature, 5))
      ) MR;
  ```

  This query's output includes the same two matches for Sensors 1 and 2:

  ```plaintext
  sensor_id                 firstTs firstTemp                   lastTs lastTemp
          1 2023-04-03 02:00:01.000      40.0  2023-04-03 02:00:13.000     47.0
          2 2023-04-03 02:00:02.000      59.0  2023-04-03 02:00:14.000     53.0
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

  Run following SQL statements to create the `temperature_readings` table backed by Kafka running in Docker, and populate it with
  test data.

  ```sql
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      ts TIMESTAMP(3),
      -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
      WATERMARK FOR ts AS ts
  )  DISTRIBUTED BY (sensor_id) INTO 1 BUCKETS;
  ```

  ```sql
  INSERT INTO temperature_readings VALUES
      (0, 55, TO_TIMESTAMP('2023-04-03 02:00:00')),
      (1, 40, TO_TIMESTAMP('2023-04-03 02:00:01')),
      (2, 59, TO_TIMESTAMP('2023-04-03 02:00:02')),
      (0, 50, TO_TIMESTAMP('2023-04-03 02:00:03')),
      (1, 42, TO_TIMESTAMP('2023-04-03 02:00:04')),
      (2, 57, TO_TIMESTAMP('2023-04-03 02:00:05')),
      (0, 52, TO_TIMESTAMP('2023-04-03 02:00:06')),
      (1, 43, TO_TIMESTAMP('2023-04-03 02:00:07')),
      (2, 56, TO_TIMESTAMP('2023-04-03 02:00:08')),
      (0, 49, TO_TIMESTAMP('2023-04-03 02:00:09')),
      (1, 45, TO_TIMESTAMP('2023-04-03 02:00:10')),
      (2, 55, TO_TIMESTAMP('2023-04-03 02:00:11')),
      (0, 53, TO_TIMESTAMP('2023-04-03 02:00:12')),
      (1, 47, TO_TIMESTAMP('2023-04-03 02:00:13')),
      (2, 53, TO_TIMESTAMP('2023-04-03 02:00:14'));
  ```

  This `INSERT` statement generates temperature readings for 3 sensors (5 readings per sensor). Sensor 0's temperature fluctuates, Sensor 1's temperatures are monotonically increasing, and Sensor 2's are monotonically decreasing.

  | Sensor | Temp 1 | Temp 2 | Temp 3 | Temp 4 | Temp 5 |
  |--------|--------|--------|--------|--------|--------|
  | 0      | 55     | 50     | 52     | 49     | 53     |
  | 1      | 40     | 42     | 43     | 45     | 47     |
  | 2      | 59     | 57     | 56     | 55     | 53     |

  Now, run the example query from above to find any case where three readings for a given sensor are above 51, then below 51, and then above 51 again:
  
  ```sql
  SELECT *
  FROM temperature_readings
      MATCH_RECOGNIZE(
          PARTITION BY sensor_id
          ORDER BY ts ASC
          MEASURES
              A.temperature AS firstTemp,
              A.ts as firstTs,
              B.temperature AS middleTemp,
              B.ts as middleTs,
              C.temperature AS lastTemp,
              C.ts as lastTs
          ONE ROW PER MATCH
          AFTER MATCH SKIP PAST LAST ROW
          PATTERN (A B C)
          DEFINE
              A AS A.temperature > 51,
              B AS B.temperature < 51,
              C AS C.temperature > 51
      ) MR;
  ```
  Observe that Sensor 0's first three readings (55, 50, 52) are the only match. Why aren't the last three readings (52, 49, 53) also a match? Recall that the `AFTER MATCH` strategy of skipping past the last row will resume *after* the reading of 52, which is too far along to recognize the (52, 49, 53) sequence. If you run the same query again but substitute the after match strategy `AFTER MATCH SKIP TO NEXT ROW`, then this second sequence would be returned because the pattern searching would resume at the second reading for Sensor 0 instead of the fourth.

  Now let's run a more interesting pattern matching query to find cases where the temperature at a sensor has increased for 5 consecutive readings. To do this, we use a quantifier `{5}` in our pattern, and the pattern variable itself uses the [`LAST`](https://docs.confluent.io/cloud/current/flink/reference/queries/match_recognize.html#logical-offsets) logical offset operator in order to compare the temperature to that of the previous matching event. We must also include the condition `LAST(TEMP_UP.temperature, 1) IS NULL` to handle the first potential event in the pattern of 5 events that we're looking for. Putting it all together, the following query will find Sensor 1's 5 consecutive temperature increases (40, 42, 43, 45, 47). In the `MATCHES` clause we only output the first and last timestamp and temperature readings.

  ```sql
  SELECT *
  FROM temperature_readings
      MATCH_RECOGNIZE(
          PARTITION BY sensor_id
          ORDER BY ts ASC
          MEASURES
              FIRST(TEMP_UP.ts) AS firstTs,
              FIRST(TEMP_UP.temperature) AS firstTemp,
              LAST(TEMP_UP.ts) AS lastTs,
              LAST(TEMP_UP.temperature) AS lastTemp
          ONE ROW PER MATCH
          AFTER MATCH SKIP PAST LAST ROW
          PATTERN (TEMP_UP{5})
          DEFINE
            TEMP_UP AS
                LAST(TEMP_UP.temperature, 1) IS NULL OR TEMP_UP.temperature > LAST(TEMP_UP.temperature, 1)
      ) MR;
  ```

  As a final step, let's now find sequences of readings that are *either* all increasing or all decreasing. The `PATTERN` component of `MATCH_RECOGNIZE` doesn't support Boolean logic, so to accomplish this you can either use a `UNION` of two queries, or use one query that explicitly spells out 5 increasing or decreasing temperatures using the `LAST` logical offset operator.
  
  Here's what the `UNION` approach would look like:

  ```sql
  (SELECT *
   FROM temperature_readings
       MATCH_RECOGNIZE(
           PARTITION BY sensor_id
           ORDER BY ts ASC
           MEASURES
               FIRST(TEMP_UP.ts) AS firstTs,
               FIRST(TEMP_UP.temperature) AS firstTemp,
               LAST(TEMP_UP.ts) AS lastTs,
               LAST(TEMP_UP.temperature) AS lastTemp
           ONE ROW PER MATCH
           AFTER MATCH SKIP PAST LAST ROW
           PATTERN (TEMP_UP{5})
           DEFINE
               TEMP_UP AS
                   LAST(TEMP_UP.temperature, 1) IS NULL OR TEMP_UP.temperature > LAST(TEMP_UP.temperature, 1)
       ) MR)
  UNION
  (SELECT *
   FROM temperature_readings
       MATCH_RECOGNIZE(
           PARTITION BY sensor_id
           ORDER BY ts ASC
           MEASURES
               FIRST(TEMP_DOWN.ts) AS firstTs,
               FIRST(TEMP_DOWN.temperature) AS firstTemp,
               LAST(TEMP_DOWN.ts) AS lastTs,
               LAST(TEMP_DOWN.temperature) AS lastTemp
           ONE ROW PER MATCH
           AFTER MATCH SKIP PAST LAST ROW
           PATTERN (TEMP_DOWN{5})
           DEFINE
               TEMP_DOWN AS
                   LAST(TEMP_DOWN.temperature, 1) IS NULL OR TEMP_DOWN.temperature < LAST(TEMP_DOWN.temperature, 1)
       ) MR);
  ```

  Observe that results for both Sensors 1 and 2 are returned.

  The second approach to this, where we explicitly spell out the sequence of 5 increases or decreases in the pattern variable definition, looks like this. Note that, to handle first few events in the pattern we are looking for, we need to check `LAST(TEMP_SAME_DIRECTION.temperature, <offset>)` for `NULL`.

  ```sql
  SELECT *
  FROM temperature_readings
      MATCH_RECOGNIZE(
          PARTITION BY sensor_id
          ORDER BY ts ASC
          MEASURES
              FIRST(TEMP_SAME_DIRECTION.ts) AS firstTs,
              FIRST(TEMP_SAME_DIRECTION.temperature) AS firstTemp,
              LAST(TEMP_SAME_DIRECTION.ts) AS lastTs,
              LAST(TEMP_SAME_DIRECTION.temperature) AS lastTemp
          ONE ROW PER MATCH
          AFTER MATCH SKIP PAST LAST ROW
          PATTERN (TEMP_SAME_DIRECTION{5})
          DEFINE
            TEMP_SAME_DIRECTION AS
                (LAST(TEMP_SAME_DIRECTION.temperature, 1) IS NULL OR TEMP_SAME_DIRECTION.temperature > LAST(TEMP_SAME_DIRECTION.temperature, 1))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 2) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 1) > LAST(TEMP_SAME_DIRECTION.temperature, 2))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 3) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 2) > LAST(TEMP_SAME_DIRECTION.temperature, 3))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 4) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 3) > LAST(TEMP_SAME_DIRECTION.temperature, 4))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 5) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 4) > LAST(TEMP_SAME_DIRECTION.temperature, 5))
                OR
                (LAST(TEMP_SAME_DIRECTION.temperature, 1) IS NULL OR TEMP_SAME_DIRECTION.temperature < LAST(TEMP_SAME_DIRECTION.temperature, 1))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 2) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 1) < LAST(TEMP_SAME_DIRECTION.temperature, 2))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 3) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 2) < LAST(TEMP_SAME_DIRECTION.temperature, 3))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 4) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 3) < LAST(TEMP_SAME_DIRECTION.temperature, 4))
                  AND (LAST(TEMP_SAME_DIRECTION.temperature, 5) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 4) < LAST(TEMP_SAME_DIRECTION.temperature, 5))
      ) MR;
  ```

  This query's output includes the same two matches for Sensors 1 and 2:

  ![Query output](https://raw.githubusercontent.com/confluentinc/tutorials/master/pattern-matching/flinksql/img/query-out)

</details>
