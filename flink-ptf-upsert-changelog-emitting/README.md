<!-- title: How to emit an upserting changelog from an Apache Flink® Process Table Function (PTF) -->
<!-- description: Learn how to emit a corrective, upserting changelog (+I/-U/+U/-D) from a stateful Apache Flink® Process Table Function (PTF), using a live order-status tracker as a concrete example. -->

# How to emit an upserting changelog from an Apache Flink® Process Table Function (PTF)

Most Process Table Functions (PTFs) emit plain, append-only rows: once a row is out, it's out for good. Some use cases need the opposite: a *current-state* view that corrects itself as new information arrives, and that shrinks when a key should disappear entirely.

This tutorial implements that pattern as a Flink [Process Table Function (PTF)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html), using order status tracking as the concrete example. Given a stream of order lifecycle events (`PLACED`, `FULFILLED`, `SHIPPED`, `DELIVERED`, `CANCELED`) keyed by `order_id`, the `OrderStatusTracker` PTF in this example maintains each order's current status (`PENDING`, `SHIPPED`, or `DELIVERED`) and emits a row only when that status changes. If an order is canceled, the PTF deletes its row entirely rather than emitting one more update.

This example highlights an upserting changelog output: a changelog that also emits update and delete rows, so the output reflects only the current state of each key. `OrderStatusTracker` implements [`ChangelogFunction`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/functions/ChangelogFunction.html) and calls `collect` with a [`RowKind`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/types/RowKind.html) on each row, so it can emit `+I` for a brand-new order, an update for a status change, and `-D` to remove a canceled order. This tutorial creates a downstream table of order states using `CREATE TABLE ... AS SELECT` (CTAS).

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

This section creates the Confluent Cloud resources needed to run this tutorial, using the `confluent-quickstart` CLI plugin. If you already have the Confluent Cloud resources required to run Flink SQL statements and Table API programs, you may skip to the [next step](#inspect-the-ptf-code).

Install the plugin by running:

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
    --create-kafka-key \
    --kafka-java-properties-file ./flink-ptf-upsert-changelog-emitting/order-status-ptf/src/main/resources/cloud.properties \
    --max-cfu 10
```

The plugin should complete in under a minute.

## Inspect the PTF code

The [`OrderStatusTracker`](order-status-ptf/src/main/java/io/confluent/developer/OrderStatusTracker.java) class (located under `flink-ptf-upsert-changelog-emitting/order-status-ptf`) extends `ProcessTableFunction` and implements a single `eval` method. A few things are worth calling out:

* **`implements ChangelogFunction`.** This is what allows the PTF to emit anything other than plain inserts. Implementing it requires overriding `getChangelogMode`, which tells the planner which kinds of changes (`INSERT`, `UPDATE_BEFORE`, `UPDATE_AFTER`, `DELETE`) the function may produce.
* **`collect(Row.ofKind(...))` instead of `collect(...)`.** A PTF's `eval` method normally calls `collect` with a plain POJO or `Row`, which the runtime always tags as an insert. Tagging a `Row` with an explicit `RowKind` is how a PTF controls whether a given output row is an insert, an update, or a delete.
* **No `order_id` in the function's own output.** The framework automatically prepends the `PARTITION BY` key to every output row, carrying whatever `RowKind` the function emits. Since `order_id` *is* the partition key here, `OrderStatusTracker`'s own `@DataTypeHint` declares only `status`; redeclaring `order_id` in the function's output would collide with the automatically forwarded column and break the requirement that the upsert key equal the `PARTITION BY` key.

```java
@DataTypeHint("ROW<status STRING>")
public class OrderStatusTracker extends ProcessTableFunction<Row> implements ChangelogFunction {

    public static class OrderState {
        public String status;
    }

    @Override
    public ChangelogMode getChangelogMode(ChangelogContext changelogContext) {
        return ChangelogMode.upsert(false);
    }

    public void eval(
            Context ctx,
            @StateHint OrderState state,
            @ArgumentHint(name = "input", value = SET_SEMANTIC_TABLE) Row input
    ) {
        String eventType = input.getFieldAs("event_type");

        if ("CANCELED".equals(eventType)) {
            if (state.status != null) {
                collect(Row.ofKind(RowKind.DELETE, state.status));
            }
            ctx.clearAll();
            return;
        }

        String newStatus = toStatus(eventType);
        if (newStatus == null || newStatus.equals(state.status)) {
            return;
        }

        if (state.status == null) {
            collect(Row.ofKind(RowKind.INSERT, newStatus));
        } else {
            collect(Row.ofKind(RowKind.UPDATE_AFTER, newStatus));
        }
        state.status = newStatus;
    }
}
```

`PLACED` and `FULFILLED` both map to `PENDING`, `SHIPPED` maps to `SHIPPED`, and `DELIVERED` maps to `DELIVERED`. If the mapped status matches the order's current status (e.g. `FULFILLED` arriving right after `PLACED`), nothing is emitted at all, because an upserting PTF doesn't need to say anything when nothing changed.

## Deploy and register the PTF

This section deploys the PTF to Confluent Cloud, assuming the infrastructure created in the [Provision Confluent Cloud infrastructure](#provision-confluent-cloud-infrastructure) section above.

First, build an uberjar containing all dependencies:

```shell
./gradlew flink-ptf-upsert-changelog-emitting:order-status-ptf:shadowJar
```

Upload the JAR as a Flink artifact:

```shell
confluent flink artifact create order_status_ptf \
    --artifact-file ./flink-ptf-upsert-changelog-emitting/order-status-ptf/build/libs/order-status-ptf-all.jar \
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
CREATE FUNCTION OrderStatusTracker
AS 'io.confluent.developer.OrderStatusTracker'
USING JAR 'confluent-artifact://cfa-123456';
```

## Try it out

Create a single-partition table of order events:

```sql
CREATE TABLE order_events (
  order_id INT NOT NULL,
  event_type STRING,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time
) DISTRIBUTED INTO 1 BUCKETS;
```

Insert sample events for three orders. Order `101` runs the full happy path to `DELIVERED`. Order `102` is canceled right after being placed. Order `103` is only `PLACED`.

```sql
INSERT INTO order_events VALUES
  (101, 'PLACED',    TIMESTAMP '2026-08-01 09:00:00'),
  (102, 'PLACED',    TIMESTAMP '2026-08-01 09:00:02'),
  (103, 'PLACED',    TIMESTAMP '2026-08-01 09:00:03'),
  (101, 'FULFILLED', TIMESTAMP '2026-08-01 09:00:10'),
  (102, 'CANCELED',  TIMESTAMP '2026-08-01 09:00:15'),
  (101, 'SHIPPED',   TIMESTAMP '2026-08-01 09:00:30'),
  (101, 'DELIVERED', TIMESTAMP '2026-08-01 09:01:00');
```

Create a downstream table for the current order status using `CREATE TABLE AS SELECT` (CTAS), with a `PRIMARY KEY` matching the PTF's `PARTITION BY` key:

```sql
CREATE TABLE order_status (
    PRIMARY KEY (order_id) NOT ENFORCED
) DISTRIBUTED INTO 1 BUCKETS
WITH ('changelog.mode' = 'upsert') AS
SELECT
    order_id,
    status
FROM OrderStatusTracker(
    input => TABLE order_events PARTITION BY order_id
);
```

Now query the resulting table:

```sql
SELECT * FROM order_status;
```

The SQL client's default table result mode renders the topic's current, fully-materialized state rather than the underlying changelog, so you should see something like the following:

  ```plaintext
  order_id                         status
       101                      DELIVERED
       103                        PENDING
  ```

Notice three things:

* At any point, there's only ever one row alive per `order_id`. A new status always retracts the old one first.
* Order `101` ends at `DELIVERED` and `103` ends at `PENDING`.
* Order `102` ends on a delete with no insert after it: it was canceled, so it's gone from `order_status` entirely.

If you'd rather see the underlying changelog that produced this materialized state, switch the SQL client into changelog result mode by entering `m`. You should see something like the following (the exact interleaving between different orders can vary, but the sequence of ops within a single `order_id` is fixed):

  ```plaintext
  Operation order_id status
  +I        101      SHIPPED
  +I        103      PENDING
  +U        101      DELIVERED
  ```

> **Calling the PTF more than once?** A stateful, set-semantic PTF needs a unique ID per invocation. With a single call the function name is used automatically. If you call `OrderStatusTracker` multiple times in one statement, add a `uid => '...'` argument to each call.

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

  The [`OrderStatusTracker`](order-status-ptf/src/main/java/io/confluent/developer/OrderStatusTracker.java) class (located under `flink-ptf-upsert-changelog-emitting/order-status-ptf`) extends `ProcessTableFunction` and implements a single `eval` method. A few things are worth calling out:

  * **`implements ChangelogFunction`.** This is what allows the PTF to emit anything other than plain inserts. Implementing it requires overriding `getChangelogMode`, which tells the planner which kinds of changes (`INSERT`, `UPDATE_BEFORE`, `UPDATE_AFTER`, `DELETE`) the function may produce.
  * **`collect(Row.ofKind(...))` instead of `collect(...)`.** A PTF's `eval` method normally calls `collect` with a plain POJO or `Row`, which the runtime always tags as an insert. Tagging a `Row` with an explicit `RowKind` is how a PTF controls whether a given output row is an insert, an update, or a delete.
  * **No `order_id` in the function's own output.** The framework automatically prepends the `PARTITION BY` key to every output row, carrying whatever `RowKind` the function emits. Since `order_id` *is* the partition key here, `OrderStatusTracker`'s own `@DataTypeHint` declares only `status`; redeclaring `order_id` in the function's output would collide with the automatically forwarded column and break the requirement that the upsert key equal the `PARTITION BY` key.

  ```java
  @DataTypeHint("ROW<status STRING>")
  public class OrderStatusTracker extends ProcessTableFunction<Row> implements ChangelogFunction {

      public static class OrderState {
          public String status;
      }

      @Override
      public ChangelogMode getChangelogMode(ChangelogContext changelogContext) {
          return ChangelogMode.upsert(false);
      }

      public void eval(
              Context ctx,
              @StateHint OrderState state,
              @ArgumentHint(name = "input", value = SET_SEMANTIC_TABLE) Row input
      ) {
          String eventType = input.getFieldAs("event_type");

          if ("CANCELED".equals(eventType)) {
              if (state.status != null) {
                  collect(Row.ofKind(RowKind.DELETE, state.status));
              }
              ctx.clearAll();
              return;
          }

          String newStatus = toStatus(eventType);
          if (newStatus == null || newStatus.equals(state.status)) {
              return;
          }

          if (state.status == null) {
              collect(Row.ofKind(RowKind.INSERT, newStatus));
          } else {
              collect(Row.ofKind(RowKind.UPDATE_AFTER, newStatus));
          }
          state.status = newStatus;
      }
  }
  ```

  `PLACED` and `FULFILLED` both map to `PENDING`, `SHIPPED` maps to `SHIPPED`, and `DELIVERED` maps to `DELIVERED`. If the mapped status matches the order's current status (e.g. `FULFILLED` arriving right after `PLACED`), nothing is emitted at all, because an upserting PTF doesn't need to say anything when nothing changed.

  ## Deploy and register the PTF

  First, compile the PTF into an uberjar:

  ```shell
  ./gradlew flink-ptf-upsert-changelog-emitting:order-status-ptf:shadowJar
  ```

  Copy the JAR into the Flink SQL client container:

  ```shell
  docker cp flink-ptf-upsert-changelog-emitting/order-status-ptf/build/libs/order-status-ptf-all.jar flink-sql-client:/opt/flink/lib
  ```

  Open a Flink SQL shell:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Once in the SQL shell, load the JAR file:

  ```shell
  ADD JAR '/opt/flink/lib/order-status-ptf-all.jar';
  ```

  Register the PTF as a function:

  ```shell
  CREATE FUNCTION OrderStatusTracker
  AS 'io.confluent.developer.OrderStatusTracker'
  USING JAR '/opt/flink/lib/order-status-ptf-all.jar';
  ```

  ## Try it out

  First, from your local machine, create the backing Kafka topics:

  ```shell
  docker exec broker kafka-topics --bootstrap-server broker:9092 --create --topic order-events --partitions 1
  docker exec broker kafka-topics --bootstrap-server broker:9092 --create --topic order-status --partitions 1
  ```

  Back in the Flink SQL shell, create a Kafka-backed table over the `order-events` topic:

  ```shell
  CREATE TABLE order_events (
      order_id INT,
      event_type STRING,
      event_time TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
      WATERMARK FOR event_time AS event_time
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'order-events',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'order_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```

  Insert sample events for three orders. Order `101` runs the full happy path to `DELIVERED`. Order `102` is canceled right after being placed. Order `103` is only `PLACED`.

  ```shell
  INSERT INTO order_events VALUES
      (101, 'PLACED',    TIMESTAMP '2026-08-01 09:00:00'),
      (102, 'PLACED',    TIMESTAMP '2026-08-01 09:00:02'),
      (103, 'PLACED',    TIMESTAMP '2026-08-01 09:00:03'),
      (101, 'FULFILLED', TIMESTAMP '2026-08-01 09:00:10'),
      (102, 'CANCELED',  TIMESTAMP '2026-08-01 09:00:15'),
      (101, 'SHIPPED',   TIMESTAMP '2026-08-01 09:00:30'),
      (101, 'DELIVERED', TIMESTAMP '2026-08-01 09:01:00');
  ```

  Create the downstream table explicitly, backed by the `upsert-kafka` connector with a `PRIMARY KEY` matching the PTF's `PARTITION BY` key:

  ```shell
  CREATE TABLE order_status (
      order_id INT,
      status STRING,
      PRIMARY KEY (order_id) NOT ENFORCED
  ) WITH (
      'connector' = 'upsert-kafka',
      'topic' = 'order-status',
      'properties.bootstrap.servers' = 'broker:9092',
      'key.format' = 'raw',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081'
  );
  ```

  Then populate it by calling the PTF:

  ```shell
  INSERT INTO order_status
  SELECT order_id, status
  FROM OrderStatusTracker(
      input => TABLE order_events PARTITION BY order_id
  );
  ```

  Now query the resulting table:

  ```shell
  SELECT * FROM order_status;
  ```

  The SQL client's default table result mode renders the topic's current, fully-materialized state rather than the underlying changelog, so you should see something like the following:

  ```plaintext
  order_id                         status
       101                      DELIVERED
       103                        PENDING
  ```

  Notice three things:

  * At any point, there's only ever one row alive per `order_id`. A new status always retracts the old one first.
  * Order `101` ends at `DELIVERED` and `103` ends at `PENDING`.
  * Order `102` ends on a delete with no insert after it: it was canceled, so it's gone from `order_status` entirely.

  If you'd rather see the underlying changelog that produced this materialized state, switch the SQL client into `changelog` result mode before querying:

  ```shell
  SET 'sql-client.execution.result-mode' = 'changelog';
  SELECT * FROM order_status;
  ```

  You should see something like the following (the exact interleaving between different orders can vary, but the sequence of ops within a single `order_id` is fixed):

  ```plaintext
  op    order_id                         status
  +I         101                        PENDING
  +I         102                        PENDING
  +I         103                        PENDING
  -D         102                        PENDING
  -U         101                        PENDING
  +U         101                        SHIPPED
  -U         101                        SHIPPED
  +U         101                      DELIVERED
  ```

  ## Clean up

  From your local machine, stop the Kafka, Schema Registry, and Flink containers:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml down
  ```
</details>
