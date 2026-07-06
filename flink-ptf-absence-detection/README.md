<!-- title: How to detect the absence of an event with an Apache Flink® Process Table Function (PTF) -->
<!-- description: Learn how to detect the absence of an event with a stateful, timer-driven Apache Flink® Process Table Function (PTF), using session expiry as a concrete example. -->

# How to detect the absence of an event with an Apache Flink® Process Table Function (PTF)

Some of the most useful stream processing patterns are about what *doesn't* happen: an expected event that never arrives within a deadline. This is absence detection (also called non-occurrence or missing-event detection), and it underlies use cases like SLA and heartbeat monitoring, abandoned-cart detection, and sessionization. The general recipe is the same in every case: when an event arrives, set a timer for the deadline; if the awaited event shows up, cancel or reset the timer; if the deadline passes first, react to the absence.

This tutorial implements the pattern as a [Process Table Function (PTF)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html), using session expiry as the concrete example: given a stream of user events, the `SessionWindow` PTF groups consecutive events per user into a session and emits a one-row summary when the session ends. A session ends either when the user sends a `LOGOUT` event or after a configurable period of inactivity.

This example highlights a headline PTF capability: event-time timers. Each event resets an inactivity timer, and the PTF's `onTimer` callback fires only if no further event arrives before the session timeout elapses.

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

The [`SessionWindow`](session-window-ptf/src/main/java/io/confluent/developer/SessionWindow.java) class  (located under `flink-ptf-absence-detection/session-window-ptf`) extends `ProcessTableFunction` and implements two methods. The `eval` method runs per input row, while `onTimer` runs when a registered timer fires. Three features related to timers are worth calling out:

* **The `Context` argument.** Declaring `Context ctx` as the first `eval` parameter gives access to `ctx.timeContext(Instant.class)` for reading the current event time and scheduling timers, and to `ctx.clearAll()` for wiping a partition's state and pending timers.
* **The `REQUIRE_ON_TIME` argument trait.** Alongside `SET_SEMANTIC_TABLE`, the input table argument carries `REQUIRE_ON_TIME`. This makes the PTF time-aware and requires the SQL caller to bind an event-time column via `on_time => DESCRIPTOR(...)` (shown below). Timers are scheduled and fired against that event time.
* **A named timer.** On every event, the PTF registers a timer named `"timeout"` for `current_event_time + sessionTimeout`. Re-registering the same name *replaces* the prior timer, so the timeout only fires once a user has been idle for the full session timeout:

```java
public void eval(
        Context ctx,
        @StateHint SessionState session,
        @StateHint EventTypesState eventTypes,
        @ArgumentHint(name = "input", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row input,
        @ArgumentHint(name = "sessionTimeout") int sessionTimeout
) throws Exception {
    TimeContext<Instant> timeCtx = ctx.timeContext(Instant.class);
    // ... initialize session state on the first event, then for every event:
    eventTypes.states.add(input.getFieldAs("event_type"));
    timeCtx.registerOnTime("timeout", timeCtx.time().plus(Duration.ofSeconds(sessionTimeout)));

    if ("LOGOUT".equals(input.getFieldAs("event_type"))) {
        collect(/* COMPLETED summary */);
        ctx.clearAll();
    }
}
```

When the session timeout elapses with no new event, `onTimer` emits a `TIMEOUT` summary instead:

```java
public void onTimer(OnTimerContext onTimerCtx, SessionState session, EventTypesState eventTypes) {
    // emit a TIMEOUT summary, then clear the partition's state and timers
    onTimerCtx.clearAll();
}
```

A second `@StateHint` argument, an `EventTypesState` POJO, accumulates the ordered list of event types so the summary can report the full session sequence (e.g., `LOGIN > VIEW > LOGOUT`).

## Deploy and register the PTF

Now that we've examined the code, let's deploy the PTF to Confluent Cloud. This section assumes the Confluent Cloud infrastructure created in the [Provision Confluent Cloud infrastructure](#provision-confluent-cloud-infrastructure) section above.

This tutorial calls the PTF via Flink SQL. To invoke a PTF from the Table API instead, refer to [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-process-table-function/#call-the-ptf-via-the-table-api) for instructions and sample code.

First, build an uberjar containing all dependencies:

```shell
./gradlew flink-ptf-absence-detection:session-window-ptf:shadowJar
```

Upload the JAR as a Flink artifact:

```shell
confluent flink artifact create session_window_ptf \
    --artifact-file ./flink-ptf-absence-detection/session-window-ptf/build/libs/session-window-ptf-all.jar \
    --cloud aws \
    --region us-east-1
```

Take note of the artifact ID returned (it will look like `cfa-123456`). Next, open the Flink SQL shell:

```shell
confluent flink shell --cloud aws --region us-east-1
```

Set the active catalog and database to match your environment and cluster:

```shell
USE CATALOG flink_ptf_tutorial_environment;
USE flink_ptf_tutorial_cluster;
```

Finally, register the PTF as a function, replacing `cfa-123456` with your actual artifact ID:

```sql
CREATE FUNCTION SessionWindow
AS 'io.confluent.developer.SessionWindow'
USING JAR 'confluent-artifact://cfa-123456';
```

## Try it out

This example runs live: you'll start a session, wait in real time for it to go idle, and watch the timeout fire. Create a single-partition table of user events. The `event_time` column carries a watermark, which is the logical clock that drives the PTF's event-time timers:

```sql
CREATE TABLE user_events (
  user_id STRING,
  event_type STRING,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time
) DISTRIBUTED INTO 1 BUCKETS;
```

> **Why a single partition?** Flink advances a table's watermark to the *minimum* event time across all of its partitions, so an idle partition holds the whole watermark back. With one partition (`DISTRIBUTED INTO 1 BUCKETS`), a single new event reliably advances the watermark. As you'll see below, this fires the timer.

Start a session for `bob`, anchored to the current time with `LOCALTIMESTAMP`:

```sql
INSERT INTO user_events VALUES
  ('bob', 'LOGIN',  LOCALTIMESTAMP),
  ('bob', 'SEARCH', LOCALTIMESTAMP),
  ('bob', 'VIEW',   LOCALTIMESTAMP);
```

The last event for `bob` sets an inactivity timer for 30 seconds later. But an event-time timer does *not* fire when wall-clock time passes — it fires only when the stream's watermark advances past the timer's timestamp, and the watermark only moves forward when a newer event arrives. So right now nothing happens, no matter how long you wait.

Now wait at least 35 seconds. Then have a different user, `alice`, log in:

```sql
INSERT INTO user_events VALUES
  ('alice', 'LOGIN', LOCALTIMESTAMP);
```

Because you waited, `alice`'s `LOGIN` carries a timestamp more than 30 seconds after `bob`'s last event. When Flink processes it, the watermark jumps forward to `alice`'s event time, crossing `bob`'s expiry deadline. That watermark advancement is what fires `bob`'s inactivity timer, emitting his `TIMEOUT` summary. (`alice`'s own session is now open; it will expire only once some later event advances the watermark again.)

Query the sessions, partitioning by `user_id` and binding `event_time` as the timer clock via `on_time`:

```sql
SELECT *
FROM SessionWindow(
    input => TABLE user_events PARTITION BY user_id,
    sessionTimeout => 30,
    on_time => DESCRIPTOR(event_time)
);
```

You should see `bob`'s timed-out session. `durationMillis` is 30000 — the session timeout — since `bob`'s three events arrived together:

```plaintext
user_id durationMillis eventCount eventSequence         status  userId rowtime
bob     30095          3          LOGIN > SEARCH > VIEW TIMEOUT bob    2026-07-06 13:39:43.708
```

Had `bob` sent a `LOGOUT` instead of going idle, the PTF would have emitted a `COMPLETED` summary immediately, without waiting on the timer.

> **Tip:** To watch the timeout fire in real time, run the `SELECT` in a Flink shell *before* inserting `alice`'s login. The streaming query sits with no output during the wait, then emits `bob`'s `TIMEOUT` row the instant `alice`'s event advances the watermark past the deadline. (If you insert `alice` too soon — less than 30 seconds after `bob` — the watermark won't reach the deadline, so no row is emitted and `bob`'s session stays open until a later event advances the watermark.)

> **Calling the PTF more than once?** A stateful, set-semantic PTF needs a unique ID per invocation. With a single call the function name is used automatically. If you call `SessionWindow` multiple times in one statement, add a `uid => '...'` argument to each call.

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

  The [`SessionWindow`](session-window-ptf/src/main/java/io/confluent/developer/SessionWindow.java) class  (located under `flink-ptf-absence-detection/session-window-ptf`) extends `ProcessTableFunction` and implements two methods. The `eval` method runs per input row, while `onTimer` runs when a registered timer fires. Three features related to timers are worth calling out:

  * **The `Context` argument.** Declaring `Context ctx` as the first `eval` parameter gives access to `ctx.timeContext(Instant.class)` for reading the current event time and scheduling timers, and to `ctx.clearAll()` for wiping a partition's state and pending timers.
  * **The `REQUIRE_ON_TIME` argument trait.** Alongside `SET_SEMANTIC_TABLE`, the input table argument carries `REQUIRE_ON_TIME`. This makes the PTF time-aware and requires the SQL caller to bind an event-time column via `on_time => DESCRIPTOR(...)` (shown below). Timers are scheduled and fired against that event time.
  * **A named timer.** On every event, the PTF registers a timer named `"timeout"` for `current_event_time + sessionTimeout`. Re-registering the same name *replaces* the prior timer, so the timeout only fires once a user has been idle for the full session timeout:

  ```java
  public void eval(
          Context ctx,
          @StateHint SessionState session,
          @StateHint EventTypesState eventTypes,
          @ArgumentHint(name = "input", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) 
          Row input,
          @ArgumentHint(name = "sessionTimeout") int sessionTimeout
  ) throws Exception {
      TimeContext<Instant> timeCtx = ctx.timeContext(Instant.class);
      // ... initialize session state on the first event, then for every event:
      eventTypes.states.add(input.getFieldAs("event_type"));
      timeCtx.registerOnTime("timeout", timeCtx.time().plus(Duration.ofSeconds(sessionTimeout)));

      if ("LOGOUT".equals(input.getFieldAs("event_type"))) {
          collect(/* COMPLETED summary */);
          ctx.clearAll();
      }
  }
  ```

  When the session timeout elapses with no new event, `onTimer` emits a `TIMEOUT` summary instead:

  ```java
  public void onTimer(OnTimerContext onTimerCtx, SessionState session, EventTypesState eventTypes) {
      // emit a TIMEOUT summary, then clear the partition's state and timers
      onTimerCtx.clearAll();
  }
  ```

  A second `@StateHint` argument, an `EventTypesState` POJO, accumulates the ordered list of event types so the summary can report the full session sequence (e.g., `LOGIN > VIEW > LOGOUT`).

  ## Deploy and register the PTF

  We will call the PTF via Flink SQL. To invoke it from a Flink Table API program instead, refer to [this tutorial](https://developer.confluent.io/confluent-tutorials/flink-process-table-function/#call-the-ptf-via-the-table-api) for instructions and sample code.

  First, compile the PTF into an uberjar:

  ```shell
  ./gradlew flink-ptf-absence-detection:session-window-ptf:shadowJar
  ```

  Copy the JAR into the Flink SQL client container:

  ```shell
  docker cp flink-ptf-absence-detection/session-window-ptf/build/libs/session-window-ptf-all.jar flink-sql-client:/opt/flink/lib
  ```

  Open a Flink SQL shell:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Once in the SQL shell, load the JAR file:

  ```shell
  ADD JAR '/opt/flink/lib/session-window-ptf-all.jar';
  ```

  Register the PTF as a function:

  ```shell
  CREATE FUNCTION SessionWindow
  AS 'io.confluent.developer.SessionWindow'
  USING JAR '/opt/flink/lib/session-window-ptf-all.jar';
  ```

  ## Try it out

  This example runs live: you'll start a session, wait in real time for it to go idle, and watch the timeout fire. First, from your local machine, create the backing Kafka topic with a single partition:

  ```shell
  docker exec broker kafka-topics --bootstrap-server broker:9092 --create --topic user-events --partitions 1
  ```

  Back in the Flink SQL shell, create a Kafka-backed table over that topic. The `event_time` column carries a watermark, which is the logical clock that drives the PTF's event-time timers:

  ```shell
  CREATE TABLE user_events (
      user_id STRING,
      event_type STRING,
      event_time TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
      WATERMARK FOR event_time AS event_time
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'user-events',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'user_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  > **Why a single partition?** Flink advances a table's watermark to the *minimum* event time across all of its partitions, so an idle partition would hold the watermark back and the timer would never fire. With one partition, a single new event reliably advances the watermark.

  Start a session for `bob`, anchored to the current time with `LOCALTIMESTAMP`:

  ```shell
  INSERT INTO user_events VALUES
      ('bob', 'LOGIN',  LOCALTIMESTAMP),
      ('bob', 'SEARCH', LOCALTIMESTAMP),
      ('bob', 'VIEW',   LOCALTIMESTAMP);
  ```

  `bob`'s last event sets an inactivity timer for 30 seconds later. An event-time timer fires only when the watermark advances past its timestamp — not when wall-clock time passes — and the watermark advances only when a newer event arrives. Wait at least 35 seconds, then have a different user, `alice`, log in:

  ```shell
  INSERT INTO user_events VALUES
      ('alice', 'LOGIN', LOCALTIMESTAMP);
  ```

  Because you waited, `alice`'s `LOGIN` carries a timestamp more than 30 seconds after `bob`'s last event. Processing it jumps the watermark past `bob`'s expiry deadline, and that watermark advancement is what fires `bob`'s inactivity timer, emitting his `TIMEOUT` summary.

  Query the sessions, partitioning by `user_id` and binding `event_time` as the timer clock via `on_time`:

  ```shell
  SELECT *
  FROM SessionWindow(
      input => TABLE user_events PARTITION BY user_id,
      sessionTimeout => 30,
      on_time => DESCRIPTOR(event_time)
  );
  ```

  You should see `bob`'s timed-out session. `durationMillis` is 30000 — the session timeout — since `bob`'s three events arrived together:

  ```plaintext
  user_id status   durationMillis eventCount eventSequence
  bob     TIMEOUT  30000          3          LOGIN > SEARCH > VIEW
  ```

  ## Clean up

  From your local machine, stop the Kafka, Schema Registry, and Flink containers:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml down
  ```
</details>
