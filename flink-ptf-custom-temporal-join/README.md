<!-- title: How to implement a custom temporal join with an Apache Flink® Process Table Function (PTF) -->
<!-- description: Learn how to join two independent event streams inside a stateful Apache Flink® Process Table Function (PTF), fusing temperature and humidity readings per machine/sensor into a single maintenance verdict per tumbling window. -->

# How to implement a custom temporal join with an Apache Flink® Process Table Function (PTF)

Some decisions depend on combining two independent event streams rather than aggregating a single one. Predictive maintenance is a good example: a machine's temperature sensor and its humidity sensor publish readings independently, but the maintenance verdict only makes sense once both signals are considered together over the same window of time.

This tutorial implements that fusion as a Flink [Process Table Function (PTF)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html). The `SensorFusionPtf` in this example takes two set-semantic table arguments (a stream of `temperature_reading` events and a stream of `humidity_reading` events) correlated by machine/sensor ID, and computes, once per tumbling window, whether that machine needs maintenance: `true` if the window's median temperature exceeds a configurable `temperatureThreshold` *and* its median humidity exceeds a configurable `humidityThreshold`. The window size itself is also configurable, via a `windowInterval` argument.

This example builds on two previous PTF tutorials. Like the [incremental windowing tutorial](https://developer.confluent.io/confluent-tutorials/flink-ptf-incremental-windowing/), it anchors tumbling windows to epoch time and drives window rollover from arriving events rather than timers. Unlike that tutorial, `SensorFusionPtf` stays silent while a window is in progress, emitting exactly once per window when a later reading rolls the window forward. And like the [`Median` PTF tutorial](https://developer.confluent.io/confluent-tutorials/flink-process-table-function/), it computes a median over buffered readings -- here, medians of both a temperature list and a humidity list, kept in state.

What's new here is the second table argument: `eval` is invoked once per incoming row from *either* input table, with the other table's row argument left `null`. The Flink runtime handles the join itself, co-locating rows across both tables that share the same `PARTITION BY` key value.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the `Docker instructions` section at the bottom.

> **Already have the prerequisites and Confluent Cloud set up from a previous PTF tutorial?** Skip ahead to [Inspect the PTF code](#inspect-the-ptf-code).

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* Java 17, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. Validate that `java -version` shows version 17.
* Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

## Provision Confluent Cloud infrastructure

If you already have the Confluent Cloud resources required to run Flink SQL statements and Table API programs, you may skip to the [next step](#inspect-the-ptf-code).

If you need to create the Confluent Cloud infrastructure needed to run this tutorial, the `confluent-quickstart` CLI plugin creates the resources that you need to get started with Confluent Cloud for Apache Flink. Install it by running:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin as follows to create the Confluent Cloud resources needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent flink region list --cloud <CLOUD>`.

```shell
confluent quickstart \
    --region us-east-1 \
    --cloud aws \
    --environment-name flink_ptf_tutorial_environment \
    --kafka-cluster-name flink_ptf_tutorial_cluster \
    --compute-pool-name flink_ptf_tutorial_pool \
    --max-cfu 10
```

The plugin should complete in under a minute.

## Inspect the PTF code

The [`SensorFusionPtf`](custom-temporal-join-ptf/src/main/java/io/confluent/developer/SensorFusionPtf.java) class (located under `flink-ptf-custom-temporal-join/custom-temporal-join-ptf`) extends `ProcessTableFunction` and implements a single `eval` method. A few things are worth calling out:

* **Two table arguments, one join key.** `eval` declares two `@ArgumentHint(SET_SEMANTIC_TABLE)` arguments, `temperatureReading` and `humidityReading`. When calling the PTF, each table is partitioned by its own key column (`machine_id` for temperature readings, `sensor_id` for humidity readings). The Flink runtime treats matching key values across both tables as the same virtual processor, so a `machine_id` of `M1` and a `sensor_id` of `M1` share the same state.
* **Only one table argument is non-null per call.** Rows arrive from either input stream into `eval` one at a time; the PTF checks which argument is non-null to know which stream produced the current row, and accumulates its value into the corresponding state list.
* **Tumbling window boundaries via modulo arithmetic.** Each window is anchored to epoch time rather than to the first reading seen for a machine/sensor. The window size is passed in as a `Duration` argument rather than hardcoded.
* **Emit-only-on-rollover.** This PTF does not re-emit its running aggregate on every event. It stays silent until a reading's window start no longer matches the state's stored window start. At that point, it computes the completed window's median temperature and median humidity, decides `needsMaintenance` against the caller-supplied `temperatureThreshold` and `humidityThreshold`, emits that single result, and resets its buffered lists for the new window.

```java
public void eval(
        Context ctx,
        @StateHint SensorFusionState state,
        @ArgumentHint(name = "temperature_reading", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row temperatureReading,
        @ArgumentHint(name = "humidity_reading", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row humidityReading,
        @ArgumentHint(name = "windowInterval") Duration windowInterval,
        @ArgumentHint(name = "temperatureThreshold") double temperatureThreshold,
        @ArgumentHint(name = "humidityThreshold") double humidityThreshold
) {
    TimeContext<Long> timeCtx = ctx.timeContext(Long.class);
    long currentEventTime = timeCtx.time();

    long windowStart = currentEventTime - (currentEventTime % windowInterval.toMillis());
    long windowEnd = windowStart + windowInterval.toMillis();

    // Check if we've moved to a new window - if so, emit the completed window's
    // maintenance verdict before resetting state
    if (state.windowStartMillis != -1L && state.windowStartMillis != windowStart) {
        emitWindowResult(state, temperatureThreshold, humidityThreshold);
        state.temperatures.clear();
        state.humidities.clear();
    }

    state.windowStartMillis = windowStart;
    state.windowEndMillis = windowEnd;

    // Aggregate the current reading, from whichever table produced it
    if (temperatureReading != null) {
        Double temperature = temperatureReading.getFieldAs("temperature");
        if (temperature != null) {
            state.temperatures.add(temperature);
        }
    } else if (humidityReading != null) {
        Double humidity = humidityReading.getFieldAs("humidity");
        if (humidity != null) {
            state.humidities.add(humidity);
        }
    }
}
```

> **A note on ordering.** Because the two input tables are independent streams, the Flink runtime does not guarantee any particular interleaving of their rows at the virtual processor -- a design point the [PTF documentation](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html) itself calls out for multi-table PTFs. This tutorial's sample data is ordered so that, in practice, all of a window's readings from both tables arrive before the next window's readings begin. A production implementation with stronger ordering requirements would instead wait for both tables' watermarks to pass the window boundary (via `TimeContext#tableWatermark()`) before emitting.

## Deploy and register the PTF

Now that we've examined the code, let's deploy the PTF to Confluent Cloud. This section assumes the Confluent Cloud infrastructure created in the [Provision Confluent Cloud infrastructure](#provision-confluent-cloud-infrastructure) section above.

This tutorial calls the PTF via Flink SQL. To invoke a PTF from the Table API instead, refer to [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-process-table-function/#call-the-ptf-via-the-table-api) for instructions and sample code.

First, build an uberjar containing all dependencies:

```shell
./gradlew flink-ptf-custom-temporal-join:custom-temporal-join-ptf:shadowJar
```

Upload the JAR as a Flink artifact:

```shell
confluent flink artifact create custom_temporal_join_ptf \
    --artifact-file ./flink-ptf-custom-temporal-join/custom-temporal-join-ptf/build/libs/custom-temporal-join-ptf-all.jar \
    --cloud aws \
    --region us-east-1
```

Take note of the artifact ID returned (it will look like `cfa-123456`). Next, open the Flink SQL shell:

```shell
confluent flink shell --cloud aws --region us-east-1
```

Set the active catalog and database to match your environment and cluster:

```sql
USE CATALOG flink_ptf_tutorial_environment;
USE flink_ptf_tutorial_cluster;
```

Finally, register the PTF as a function, replacing `cfa-123456` with your actual artifact ID:

```sql
CREATE FUNCTION SensorFusionPtf
AS 'io.confluent.developer.SensorFusionPtf'
USING JAR 'confluent-artifact://cfa-123456';
```

## Try it out

Create tables for the two independent event streams:

```sql
CREATE TABLE temperature_readings (
  machine_id STRING,
  temperature DOUBLE,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time
);
```

```sql
CREATE TABLE humidity_readings (
  sensor_id STRING,
  humidity DOUBLE,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time
);
```

Insert 15 temperature readings for two machines, `M1` and `M2`, spanning two five-minute windows (`09:00:00`-`09:05:00` and `09:05:00`-`09:10:00`). `M1`'s first window runs hot (76-82 degrees); `M2`'s stays well under the 75-degree threshold throughout:

```sql
INSERT INTO temperature_readings VALUES
  ('M1', 78.0, TIMESTAMP '2026-08-01 09:00:10'),
  ('M2', 60.0, TIMESTAMP '2026-08-01 09:00:15'),
  ('M1', 80.0, TIMESTAMP '2026-08-01 09:01:20'),
  ('M2', 62.0, TIMESTAMP '2026-08-01 09:01:25'),
  ('M1', 76.0, TIMESTAMP '2026-08-01 09:02:30'),
  ('M2', 58.0, TIMESTAMP '2026-08-01 09:02:35'),
  ('M1', 82.0, TIMESTAMP '2026-08-01 09:03:40'),
  ('M2', 65.0, TIMESTAMP '2026-08-01 09:03:45'),
  ('M1', 70.0, TIMESTAMP '2026-08-01 09:05:10'),
  ('M2', 55.0, TIMESTAMP '2026-08-01 09:05:15'),
  ('M1', 68.0, TIMESTAMP '2026-08-01 09:06:20'),
  ('M2', 58.0, TIMESTAMP '2026-08-01 09:06:25'),
  ('M1', 72.0, TIMESTAMP '2026-08-01 09:07:30'),
  ('M2', 60.0, TIMESTAMP '2026-08-01 09:07:35'),
  ('M1', 65.0, TIMESTAMP '2026-08-01 09:08:40');
```

Insert 15 humidity readings for the same two machines and windows. `M1`'s first window is also humid (82-90); `M2`'s first window is humid but not quite past the 80 threshold (72-80):

```sql
INSERT INTO humidity_readings VALUES
  ('M1', 85.0, TIMESTAMP '2026-08-01 09:00:12'),
  ('M2', 75.0, TIMESTAMP '2026-08-01 09:00:17'),
  ('M1', 88.0, TIMESTAMP '2026-08-01 09:01:22'),
  ('M2', 78.0, TIMESTAMP '2026-08-01 09:01:27'),
  ('M1', 82.0, TIMESTAMP '2026-08-01 09:02:32'),
  ('M2', 72.0, TIMESTAMP '2026-08-01 09:02:37'),
  ('M1', 90.0, TIMESTAMP '2026-08-01 09:03:42'),
  ('M2', 80.0, TIMESTAMP '2026-08-01 09:03:47'),
  ('M1', 70.0, TIMESTAMP '2026-08-01 09:05:12'),
  ('M2', 60.0, TIMESTAMP '2026-08-01 09:05:17'),
  ('M1', 65.0, TIMESTAMP '2026-08-01 09:06:22'),
  ('M2', 65.0, TIMESTAMP '2026-08-01 09:06:27'),
  ('M1', 68.0, TIMESTAMP '2026-08-01 09:07:32'),
  ('M2', 62.0, TIMESTAMP '2026-08-01 09:07:37'),
  ('M1', 72.0, TIMESTAMP '2026-08-01 09:08:42');
```

Query the fused maintenance verdicts, partitioning `temperature_readings` by `machine_id` and `humidity_readings` by `sensor_id`, binding `event_time` as the PTF's shared time source via `on_time`, and passing a five-minute `windowInterval` along with the temperature and humidity thresholds:

```sql
SELECT
    machine_id,
    sensor_id,
    DATE_FORMAT(windowStart, 'yyyy-MM-dd HH:mm:ss') as windowStart,
    DATE_FORMAT(windowEnd, 'yyyy-MM-dd HH:mm:ss') as windowEnd,
    needsMaintenance
FROM SensorFusionPtf(
    temperature_reading => TABLE temperature_readings PARTITION BY machine_id,
    humidity_reading => TABLE humidity_readings PARTITION BY sensor_id,
    windowInterval => INTERVAL '5' MINUTES,
    temperatureThreshold => CAST(75.0 AS DOUBLE),
    humidityThreshold => CAST(80.0 AS DOUBLE),
    on_time => DESCRIPTOR(event_time)
);
```

You should see 2 rows: one maintenance verdict per machine for the first window. Each is emitted only once a reading from either stream rolls that machine's window forward into the second window:

```plaintext
 machine_id sensor_id windowStart          windowEnd            needsMaintenance
 M1         M1        2026-08-01 09:00:00  2026-08-01 09:05:00  TRUE
 M2         M2        2026-08-01 09:00:00  2026-08-01 09:05:00  FALSE
```

`M1`'s median temperature (79.0) and median humidity (86.5) both cross their thresholds, so it needs maintenance. `M2`'s median temperature (61.0) stays well under 75, so it doesn't, regardless of humidity.

Notice that the second window (`09:05:00`-`09:10:00`) never gets a row for either machine. A result is only emitted when a later reading advances a machine's window past its current boundary, and no reading here arrives to roll the second window forward.

> **Calling the PTF more than once?** A stateful, set-semantic PTF needs a unique ID per invocation. With a single call, the function name is used automatically. If you call `SensorFusionPtf` multiple times in one statement, add a `uid => '...'` argument to each call.

## Tear down Confluent Cloud infrastructure

When you are done, be sure to clean up any Confluent Cloud resources created for this tutorial. Since you created all resources in a Confluent Cloud environment, you can simply delete the environment and most of the resources will be deleted (e.g., the Kafka cluster and Flink compute pool). Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `flink_ptf_tutorial_environment`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

Next, delete the Flink and artifact API keys. These API keys aren't associated with the deleted environment, so they must be deleted separately. Find the keys:

```shell
confluent api-key list --resource flink --current-user
```

Then copy each 16-character alphanumeric key and delete it:
```shell
confluent api-key delete <FLINK KEY>
confluent api-key delete <CLOUD KEY>
```

<details>
  <summary>Docker instructions</summary>

  ## Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  * Java 17, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. Validate that `java -version` shows version 17.
  * Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

  ## Start Kafka, Schema Registry, and Flink in Docker

  Start Kafka, Schema Registry, and Flink with the following command run from the top-level `tutorials` repository directory:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml up -d
  ```

  ## Inspect the PTF code

  The [`SensorFusionPtf`](custom-temporal-join-ptf/src/main/java/io/confluent/developer/SensorFusionPtf.java) class (located under `flink-ptf-custom-temporal-join/custom-temporal-join-ptf`) extends `ProcessTableFunction` and implements a single `eval` method. A few things are worth calling out:

  * **Two table arguments, one join key.** `eval` declares two `@ArgumentHint(SET_SEMANTIC_TABLE)` arguments, `temperatureReading` and `humidityReading`. When calling the PTF, each table is partitioned by its own key column (`machine_id` for temperature readings, `sensor_id` for humidity readings). The Flink runtime treats matching key values across both tables as the same virtual processor, so a `machine_id` of `M1` and a `sensor_id` of `M1` share the same state.
  * **Only one table argument is non-null per call.** Rows arrive from either input stream into `eval` one at a time; the PTF checks which argument is non-null to know which stream produced the current row, and accumulates its value into the corresponding state list.
  * **Tumbling window boundaries via modulo arithmetic.** Each window is anchored to epoch time rather than to the first reading seen for a machine/sensor. The window size is passed in as a `Duration` argument rather than hardcoded.
  * **Emit-only-on-rollover.** This PTF does not re-emit its running aggregate on every event. It stays silent until a reading's window start no longer matches the state's stored window start. At that point, it computes the completed window's median temperature and median humidity, decides `needsMaintenance` against the caller-supplied `temperatureThreshold` and `humidityThreshold`, emits that single result, and resets its buffered lists for the new window.

  ```java
  public void eval(
          Context ctx,
          @StateHint SensorFusionState state,
          @ArgumentHint(name = "temperature_reading", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row temperatureReading,
          @ArgumentHint(name = "humidity_reading", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row humidityReading,
          @ArgumentHint(name = "windowInterval") Duration windowInterval,
          @ArgumentHint(name = "temperatureThreshold") double temperatureThreshold,
          @ArgumentHint(name = "humidityThreshold") double humidityThreshold
  ) {
      TimeContext<Long> timeCtx = ctx.timeContext(Long.class);
      long currentEventTime = timeCtx.time();

      long windowStart = currentEventTime - (currentEventTime % windowInterval.toMillis());
      long windowEnd = windowStart + windowInterval.toMillis();

      // Check if we've moved to a new window - if so, emit the completed window's
      // maintenance verdict before resetting state
      if (state.windowStartMillis != -1L && state.windowStartMillis != windowStart) {
          emitWindowResult(state, temperatureThreshold, humidityThreshold);
          state.temperatures.clear();
          state.humidities.clear();
      }

      state.windowStartMillis = windowStart;
      state.windowEndMillis = windowEnd;

      // Aggregate the current reading, from whichever table produced it
      if (temperatureReading != null) {
          Double temperature = temperatureReading.getFieldAs("temperature");
          if (temperature != null) {
              state.temperatures.add(temperature);
          }
      } else if (humidityReading != null) {
          Double humidity = humidityReading.getFieldAs("humidity");
          if (humidity != null) {
              state.humidities.add(humidity);
          }
      }
  }
  ```

  > **A note on ordering.** Because the two input tables are independent streams, the Flink runtime does not guarantee any particular interleaving of their rows at the virtual processor -- a design point the [PTF documentation](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html) itself calls out for multi-table PTFs. This tutorial's sample data is ordered so that, in practice, all of a window's readings from both tables arrive before the next window's readings begin. A production implementation with stronger ordering requirements would instead wait for both tables' watermarks to pass the window boundary (via `TimeContext#tableWatermark()`) before emitting.

  ## Deploy and register the PTF

  We will call the PTF via Flink SQL. To invoke it from a Flink Table API program instead, refer to [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-process-table-function/#call-the-ptf-via-the-table-api) for instructions and sample code.

  First, compile the PTF into an uberjar:

  ```shell
  ./gradlew flink-ptf-custom-temporal-join:custom-temporal-join-ptf:shadowJar
  ```

  Copy the JAR into the Flink SQL client container:

  ```shell
  docker cp flink-ptf-custom-temporal-join/custom-temporal-join-ptf/build/libs/custom-temporal-join-ptf-all.jar \
      flink-sql-client:/opt/flink/lib
  ```

  Open a Flink SQL shell:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Once in the SQL shell, load the JAR file:

  ```shell
  ADD JAR '/opt/flink/lib/custom-temporal-join-ptf-all.jar';
  ```

  Register the PTF as a function:

  ```shell
  CREATE FUNCTION SensorFusionPtf
  AS 'io.confluent.developer.SensorFusionPtf'
  USING JAR '/opt/flink/lib/custom-temporal-join-ptf-all.jar';
  ```

  ## Try it out

  First, from your local machine, create the backing Kafka topics:

  ```shell
  docker exec broker kafka-topics --bootstrap-server broker:9092 --create --topic temperature-readings --partitions 1
  docker exec broker kafka-topics --bootstrap-server broker:9092 --create --topic humidity-readings --partitions 1
  ```

  > **Why a single partition?** Since the two input tables are independent streams, this tutorial keeps each topic to a single partition to keep the relative ordering of readings within -- and, in practice, across -- the two streams easy to follow when you run the query below.

  Back in the Flink SQL shell, create Kafka-backed tables over those topics:

  ```shell
  CREATE TABLE temperature_readings (
      machine_id STRING,
      temperature DOUBLE,
      event_time TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
      WATERMARK FOR event_time AS event_time
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'temperature-readings',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'machine_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  ```shell
  CREATE TABLE humidity_readings (
      sensor_id STRING,
      humidity DOUBLE,
      event_time TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
      WATERMARK FOR event_time AS event_time
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'humidity-readings',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'sensor_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  Insert 15 temperature readings for two machines, `M1` and `M2`, spanning two five-minute windows (`09:00:00`-`09:05:00` and `09:05:00`-`09:10:00`). `M1`'s first window runs hot (76-82 degrees); `M2`'s stays well under the 75-degree threshold throughout:

  ```shell
  INSERT INTO temperature_readings VALUES
      ('M1', 78.0, TIMESTAMP '2026-08-01 09:00:10'),
      ('M2', 60.0, TIMESTAMP '2026-08-01 09:00:15'),
      ('M1', 80.0, TIMESTAMP '2026-08-01 09:01:20'),
      ('M2', 62.0, TIMESTAMP '2026-08-01 09:01:25'),
      ('M1', 76.0, TIMESTAMP '2026-08-01 09:02:30'),
      ('M2', 58.0, TIMESTAMP '2026-08-01 09:02:35'),
      ('M1', 82.0, TIMESTAMP '2026-08-01 09:03:40'),
      ('M2', 65.0, TIMESTAMP '2026-08-01 09:03:45'),
      ('M1', 70.0, TIMESTAMP '2026-08-01 09:05:10'),
      ('M2', 55.0, TIMESTAMP '2026-08-01 09:05:15'),
      ('M1', 68.0, TIMESTAMP '2026-08-01 09:06:20'),
      ('M2', 58.0, TIMESTAMP '2026-08-01 09:06:25'),
      ('M1', 72.0, TIMESTAMP '2026-08-01 09:07:30'),
      ('M2', 60.0, TIMESTAMP '2026-08-01 09:07:35'),
      ('M1', 65.0, TIMESTAMP '2026-08-01 09:08:40');
  ```

  Insert 15 humidity readings for the same two machines and windows. `M1`'s first window is also humid (82-90); `M2`'s first window is humid but not quite past the 80 threshold (72-80):

  ```shell
  INSERT INTO humidity_readings VALUES
      ('M1', 85.0, TIMESTAMP '2026-08-01 09:00:12'),
      ('M2', 75.0, TIMESTAMP '2026-08-01 09:00:17'),
      ('M1', 88.0, TIMESTAMP '2026-08-01 09:01:22'),
      ('M2', 78.0, TIMESTAMP '2026-08-01 09:01:27'),
      ('M1', 82.0, TIMESTAMP '2026-08-01 09:02:32'),
      ('M2', 72.0, TIMESTAMP '2026-08-01 09:02:37'),
      ('M1', 90.0, TIMESTAMP '2026-08-01 09:03:42'),
      ('M2', 80.0, TIMESTAMP '2026-08-01 09:03:47'),
      ('M1', 70.0, TIMESTAMP '2026-08-01 09:05:12'),
      ('M2', 60.0, TIMESTAMP '2026-08-01 09:05:17'),
      ('M1', 65.0, TIMESTAMP '2026-08-01 09:06:22'),
      ('M2', 65.0, TIMESTAMP '2026-08-01 09:06:27'),
      ('M1', 68.0, TIMESTAMP '2026-08-01 09:07:32'),
      ('M2', 62.0, TIMESTAMP '2026-08-01 09:07:37'),
      ('M1', 72.0, TIMESTAMP '2026-08-01 09:08:42');
  ```

  Query the fused maintenance verdicts, partitioning `temperature_readings` by `machine_id` and `humidity_readings` by `sensor_id`, binding `event_time` as the PTF's shared time source via `on_time`, and passing a five-minute `windowInterval` along with the temperature and humidity thresholds:

  ```sql
  SELECT
      machine_id,
      sensor_id,
      DATE_FORMAT(windowStart, 'yyyy-MM-dd HH:mm:ss') as windowStart,
      DATE_FORMAT(windowEnd, 'yyyy-MM-dd HH:mm:ss') as windowEnd,
      needsMaintenance
  FROM SensorFusionPtf(
      temperature_reading => TABLE temperature_readings PARTITION BY machine_id,
      humidity_reading => TABLE humidity_readings PARTITION BY sensor_id,
      windowInterval => INTERVAL '5' MINUTES,
      temperatureThreshold => CAST(75.0 AS DOUBLE),
      humidityThreshold => CAST(80.0 AS DOUBLE),
      on_time => DESCRIPTOR(event_time)
  );
  ```

  You should see 2 rows: one maintenance verdict per machine for the first window. Each is emitted only once a reading from either stream rolls that machine's window forward into the second window:

  ```plaintext
   machine_id sensor_id windowStart          windowEnd            needsMaintenance
   M1         M1        2026-08-01 09:00:00  2026-08-01 09:05:00  TRUE
   M2         M2        2026-08-01 09:00:00  2026-08-01 09:05:00  FALSE
  ```

  `M1`'s median temperature (79.0) and median humidity (86.5) both cross their thresholds, so it needs maintenance. `M2`'s median temperature (61.0) stays well under 75, so it doesn't, regardless of humidity.

  Notice that the second window (`09:05:00`-`09:10:00`) never gets a row for either machine. A result is only emitted when a later reading advances a machine's window past its current boundary, and no reading here arrives to roll the second window forward.

  ## Clean up

  From your local machine, stop the Kafka, Schema Registry, and Flink containers:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml down
  ```
</details>
