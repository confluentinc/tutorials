<!-- title: How to emit windowed outputs incrementally with an Apache Flink® Process Table Function (PTF) -->
<!-- description: Learn how to emit incremental windowed aggregates with a stateful Apache Flink® Process Table Function (PTF), using an early-firing tumbling window over stock trades as a concrete example. -->

# How to emit windowed outputs incrementally with an Apache Flink® Process Table Function (PTF)

A traditional tumbling window waits for the window to close before emitting anything, which trades latency for a single final result per key per window. Some use cases want the opposite trade-off: a low-latency signal that continues to update the current window's output, even if that means seeing the same window's result more than once.

This tutorial implements that "early-firing" pattern as a Flink [Process Table Function (PTF)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html). Given a stream of stock trades, the `IncrementalOutputWindow` PTF in this example groups trades per ticker into a configurable tumbling window and emits the window's running trade count and average price on every incoming trade, not just when the window closes. Each row also carries an `isFinal` flag: `false` for every incremental emission, and `true` exactly once per window when the PTF re-emits the just-completed window's aggregate one last time as its final result.

This example highlights a different flavor of stateful PTF than the one in the [absence detection tutorial](https://developer.confluent.io/confluent-tutorials/flink-ptf-absence-detection/): instead of driving state transitions with event-time timers, this PTF reacts only to arriving events, comparing each trade's window boundary against the previously stored one.

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

The [`IncrementalOutputWindow`](incremental-window-ptf/src/main/java/io/confluent/developer/IncrementalOutputWindow.java) class (located under `flink-ptf-incremental-windowing/incremental-window-ptf`) extends `ProcessTableFunction` and implements a single `eval` method. A few things are worth calling out:

* **Tumbling window boundaries via modulo arithmetic.** Given the configured `windowInterval` and the current trade's event time, `windowStart = currentEventTime - (currentEventTime % windowInterval.toMillis())` finds the start of the tumbling window the trade belongs to. Windows are anchored to epoch time, not to the first trade seen for a ticker.
* **Emit-then-reset on window rollover.** When a trade's window start no longer matches the state's stored window start, the PTF has crossed into a new window. It emits the completed window's aggregate one final time (`isFinal = true`) before zeroing out the running count and price sum for the new window.
* **The `isFinal` flag.** Every other emission sets `isFinal = false`. A consumer of this PTF's output can filter on `isFinal = true` to get exactly one authoritative row per closed window, or read every row for a live, continuously refining view.

```java
public void eval(
    Context ctx,
    @StateHint WindowState state,
    @ArgumentHint(name = "input", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row input,
    @ArgumentHint(name = "windowInterval") Duration windowInterval
) {
    TimeContext<Long> timeCtx = ctx.timeContext(Long.class);
    long currentEventTime = timeCtx.time();
    String ticker = input.getFieldAs("ticker");
    Double price = input.getFieldAs("price");

    long windowStart = currentEventTime - (currentEventTime % windowInterval.toMillis());
    long windowEnd = windowStart + windowInterval.toMillis();

    // Check if we've moved to a new window - if so, reset state, but first emit the
    // completed window's result one final time
    if (state.windowStartMillis != -1L && state.windowStartMillis != windowStart) {
        emitWindowResult(state, true);
        state.count = 0;
        state.priceSum = 0.0;
    }

    state.ticker = ticker;
    state.windowStartMillis = windowStart;
    state.windowEndMillis = windowEnd;

    // Aggregate the current trade
    state.count++;
    if (price != null) {
        state.priceSum += price;
    }

    // Immediately emit the current window result
    emitWindowResult(state, false);
}
```

## Deploy and register the PTF

Now that we've examined the code, let's deploy the PTF to Confluent Cloud. This section assumes the Confluent Cloud infrastructure created in the [Provision Confluent Cloud infrastructure](#provision-confluent-cloud-infrastructure) section above.

This tutorial calls the PTF via Flink SQL. To invoke a PTF from the Table API instead, refer to [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-process-table-function/#call-the-ptf-via-the-table-api) for instructions and sample code.

First, build an uberjar containing all dependencies:

```shell
./gradlew flink-ptf-incremental-windowing:incremental-window-ptf:shadowJar
```

Upload the JAR as a Flink artifact:

```shell
confluent flink artifact create incremental_window_ptf \
    --artifact-file ./flink-ptf-incremental-windowing/incremental-window-ptf/build/libs/incremental-window-ptf-all.jar \
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
CREATE FUNCTION IncrementalOutputWindow
AS 'io.confluent.developer.IncrementalOutputWindow'
USING JAR 'confluent-artifact://cfa-123456';
```

## Try it out

Unlike the timer-driven `SessionWindow` PTF in [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-ptf-absence-detection/), this example needs no real-time waiting: every emission is triggered directly by an incoming trade. Create a table of stock trades:

```sql
CREATE TABLE stock_trades (
  ticker STRING,
  price DOUBLE,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time
);
```

Insert 10 trades for two tickers, `ACME` and `GLBX`, spanning two minutes. With a 1-minute window, the first minute's worth of trades for each ticker (`09:00:00`-`09:01:00`) land in one window and the rest (`09:01:00`-`09:02:00`) land in the next:

```sql
INSERT INTO stock_trades VALUES
  ('ACME', 150.00, TIMESTAMP '2026-07-01 09:00:05'),
  ('GLBX', 300.00, TIMESTAMP '2026-07-01 09:00:10'),
  ('ACME', 150.50, TIMESTAMP '2026-07-01 09:00:20'),
  ('GLBX', 300.50, TIMESTAMP '2026-07-01 09:00:30'),
  ('ACME', 151.00, TIMESTAMP '2026-07-01 09:00:45'),
  ('GLBX', 301.00, TIMESTAMP '2026-07-01 09:00:50'),
  ('GLBX', 301.20, TIMESTAMP '2026-07-01 09:01:05'),
  ('ACME', 151.25, TIMESTAMP '2026-07-01 09:01:10'),
  ('GLBX', 301.80, TIMESTAMP '2026-07-01 09:01:40'),
  ('ACME', 151.75, TIMESTAMP '2026-07-01 09:01:50');
```

Query the windowed aggregates, partitioning by `ticker` and binding `event_time` as the PTF's time source via `on_time`. Pass the 1-minute window size as an interval:

```sql
SELECT
    ticker,
    avgPrice,
    numTrades,
    DATE_FORMAT(windowStart, 'yyyy-MM-dd HH:mm:ss') as windowStart,
    DATE_FORMAT(windowEnd, 'yyyy-MM-dd HH:mm:ss') as windowEnd,
    isFinal
FROM IncrementalOutputWindow(
    input => TABLE stock_trades PARTITION BY ticker,
    windowInterval => INTERVAL '60' SECONDS,
    on_time => DESCRIPTOR(event_time)
);
```

You should see 12 rows: 10 incremental emissions (one per trade) plus 2 final emissions marking the close of each ticker's first window:

```plaintext
 ticker avgPrice numTrades windowStart          windowEnd           isFinal
 ACME   150.0    1         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
 GLBX   300.0    1         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
 ACME   150.25   2         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
 GLBX   300.25   2         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
 ACME   150.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
 GLBX   300.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
 GLBX   300.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 TRUE
 GLBX   301.2    1         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
 ACME   150.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 TRUE
 ACME   151.25   1         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
 GLBX   301.5    2         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
 ACME   151.5    2         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
```

The actual interleaving of `ACME` and `GLBX` rows in your shell will follow processing order across the two partitions, which need not match the grouping above.

Notice that the second window (`09:01:00`-`09:02:00`) never gets an `isFinal = true` row for either ticker. A final emission only happens when a later trade arrives in the next window and triggers the rollover (in production, consider state TTL or timer-based cleanup so idle tickers don't retain state indefinitely).

> **Calling the PTF more than once?** A stateful, set-semantic PTF needs a unique ID per invocation. With a single call the function name is used automatically. If you call `IncrementalOutputWindow` multiple times in one statement, add a `uid => '...'` argument to each call.

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

  The [`IncrementalOutputWindow`](incremental-window-ptf/src/main/java/io/confluent/developer/IncrementalOutputWindow.java) class (located under `flink-ptf-incremental-windowing/incremental-window-ptf`) extends `ProcessTableFunction` and implements a single `eval` method. A few things are worth calling out:

  * **Tumbling window boundaries via modulo arithmetic.** Given the configured `windowInterval` and the current trade's event time, `windowStart = currentEventTime - (currentEventTime % windowInterval.toMillis())` finds the start of the tumbling window the trade belongs to. Windows are anchored to epoch time, not to the first trade seen for a ticker.
  * **Emit-then-reset on window rollover.** When a trade's window start no longer matches the state's stored window start, the PTF has crossed into a new window. It emits the completed window's aggregate one final time (`isFinal = true`) before zeroing out the running count and price sum for the new window.
  * **The `isFinal` flag.** Every other emission sets `isFinal = false`. A consumer of this PTF's output can filter on `isFinal = true` to get exactly one authoritative row per closed window, or read every row for a live, continuously refining view.

  ```java
  public void eval(
          Context ctx,
          @StateHint WindowState state,
          @ArgumentHint(name = "input", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row input,
          @ArgumentHint(name = "windowInterval") Duration windowInterval
  ) {
      TimeContext<Long> timeCtx = ctx.timeContext(Long.class);
      long currentEventTime = timeCtx.time();
      String ticker = input.getFieldAs("ticker");
      Double price = input.getFieldAs("price");

      long windowStart = currentEventTime - (currentEventTime % windowInterval.toMillis());
      long windowEnd = windowStart + windowInterval.toMillis();

      // Check if we've moved to a new window - if so, reset state, but first emit the
      // completed window's result one final time
      if (state.windowStartMillis != -1L && state.windowStartMillis != windowStart) {
          emitWindowResult(state, true);
          state.count = 0;
          state.priceSum = 0.0;
      }

      state.ticker = ticker;
      state.windowStartMillis = windowStart;
      state.windowEndMillis = windowEnd;

      // Aggregate the current trade
      state.count++;
      if (price != null) {
          state.priceSum += price;
      }

      // Immediately emit the current window result
      emitWindowResult(state, false);
  }
  ```

  ## Deploy and register the PTF

  We will call the PTF via Flink SQL. To invoke it from a Flink Table API program instead, refer to [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-process-table-function/#call-the-ptf-via-the-table-api) for instructions and sample code.

  First, compile the PTF into an uberjar:

  ```shell
  ./gradlew flink-ptf-incremental-windowing:incremental-window-ptf:shadowJar
  ```

  Copy the JAR into the Flink SQL client container:

  ```shell
  docker cp flink-ptf-incremental-windowing/incremental-window-ptf/build/libs/incremental-window-ptf-all.jar flink-sql-client:/opt/flink/lib
  ```

  Open a Flink SQL shell:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Once in the SQL shell, load the JAR file:

  ```shell
  ADD JAR '/opt/flink/lib/incremental-window-ptf-all.jar';
  ```

  Register the PTF as a function:

  ```shell
  CREATE FUNCTION IncrementalOutputWindow
  AS 'io.confluent.developer.IncrementalOutputWindow'
  USING JAR '/opt/flink/lib/incremental-window-ptf-all.jar';
  ```

  ## Try it out

  Unlike the timer-driven `SessionWindow` PTF in [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-ptf-absence-detection/), this example needs no real-time waiting: every emission is triggered directly by an incoming trade. First, from your local machine, create the backing Kafka topic:

  ```shell
  docker exec broker kafka-topics --bootstrap-server broker:9092 --create --topic stock-trades --partitions 1
  ```

  > **Why a single partition?** This PTF doesn't rely on watermark advancement the way the timer-driven `SessionWindow` PTF does, so multiple partitions would work fine too. A single partition here is just to keep this example's output order easy to follow when you run the query below.

  Back in the Flink SQL shell, create a Kafka-backed table over that topic:

  ```shell
  CREATE TABLE stock_trades (
      ticker STRING,
      price DOUBLE,
      event_time TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
      WATERMARK FOR event_time AS event_time
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'stock-trades',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'ticker',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  Insert 10 trades for two tickers, `ACME` and `GLBX`, spanning two minutes. With a 1-minute window, the first minute's worth of trades for each ticker (`09:00:00`-`09:01:00`) land in one window and the rest (`09:01:00`-`09:02:00`) land in the next:

  ```shell
  INSERT INTO stock_trades VALUES
      ('ACME', 150.00, TIMESTAMP '2026-07-01 09:00:05'),
      ('GLBX', 300.00, TIMESTAMP '2026-07-01 09:00:10'),
      ('ACME', 150.50, TIMESTAMP '2026-07-01 09:00:20'),
      ('GLBX', 300.50, TIMESTAMP '2026-07-01 09:00:30'),
      ('ACME', 151.00, TIMESTAMP '2026-07-01 09:00:45'),
      ('GLBX', 301.00, TIMESTAMP '2026-07-01 09:00:50'),
      ('GLBX', 301.20, TIMESTAMP '2026-07-01 09:01:05'),
      ('ACME', 151.25, TIMESTAMP '2026-07-01 09:01:10'),
      ('GLBX', 301.80, TIMESTAMP '2026-07-01 09:01:40'),
      ('ACME', 151.75, TIMESTAMP '2026-07-01 09:01:50');
  ```

  Query the windowed aggregates, partitioning by `ticker` and binding `event_time` as the PTF's time source via `on_time`. Pass the 1-minute window size as an interval:

  ```sql
  SELECT
      ticker,
      avgPrice,
      numTrades,
      DATE_FORMAT(windowStart, 'yyyy-MM-dd HH:mm:ss') as windowStart,
      DATE_FORMAT(windowEnd, 'yyyy-MM-dd HH:mm:ss') as windowEnd,
      isFinal
  FROM IncrementalOutputWindow(
      input => TABLE stock_trades PARTITION BY ticker,
      windowInterval => INTERVAL '60' SECONDS,
      on_time => DESCRIPTOR(event_time)
  );
  ```

  You should see 12 rows: 10 incremental emissions (one per trade) plus 2 final emissions marking the close of each ticker's first window:

  ```plaintext
   ticker avgPrice numTrades windowStart          windowEnd           isFinal
   ACME   150.0    1         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
   GLBX   300.0    1         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
   ACME   150.25   2         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
   GLBX   300.25   2         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
   ACME   150.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
   GLBX   300.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 FALSE
   GLBX   300.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 TRUE
   GLBX   301.2    1         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
   ACME   150.5    3         2026-07-01 09:00:00  2026-07-01 09:01:00 TRUE
   ACME   151.25   1         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
   GLBX   301.5    2         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
   ACME   151.5    2         2026-07-01 09:01:00  2026-07-01 09:02:00 FALSE
  ```

  Notice that the second window (`09:01:00`-`09:02:00`) never gets an `isFinal = true` row for either ticker. A final emission only happens when a later trade arrives in the next window and triggers the rollover (in production, consider state TTL or timer-based cleanup so idle tickers don't retain state indefinitely).

  ## Clean up

  From your local machine, stop the Kafka, Schema Registry, and Flink containers:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml down
  ```
</details>
