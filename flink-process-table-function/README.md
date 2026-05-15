<!-- title: How to write and deploy Apache Flink® Process Table Functions (PTFs) -->
<!-- description: In this tutorial, learn how to write and deploy Apache Flink® Process Table Functions (PTFs), with step-by-step instructions and supporting code. -->

# How to write and deploy Apache Flink® Process Table Functions (PTFs)

In this tutorial, we take a look at Flink's most flexible form of user-defined function: [Process Table Functions (PTFs)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html). Process table functions support flexible N-to-M semantics, meaning that any number of input rows can correspond to any number of output rows, and they also give developers the ability to schedule actions and access state across multiple events.

The particular function that we will write and deploy in this tutorial is one that is well-known in statistics: one that calculates the median value over a user-specified number of events per partition key (in our case, the trailing median temperature per sensor). We will first call the function with Flink SQL, and then with the Table API.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the `Docker instructions` section at the bottom.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* Since PTFs are an Early Access feature, submit a Confluent Cloud support request to enable PTF support in your organization. Include your Organization ID from [here](https://confluent.cloud/settings/organizations/) in the Confluent Cloud Console.
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* Java 17, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. Validate that `java -version` shows version 17.
* Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

## Provision Confluent Cloud infrastructure

If you already have the Confluent Cloud resources required to run Flink SQL statements and Table API programs, you may skip to the [next step](#inspect-the-ptf-code) after creating or copying the properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file) to `flink-process-table-function/table-api-cc/src/main/resources/cloud.properties` within the top-level `tutorials` directory.

If you need to create the Confluent Cloud infrastructure needed to run this tutorial, the `confluent-quickstart` CLI plugin creates the resources that you need to get started with Confluent Cloud for Apache Flink. Install it by running:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin as follows to create the Confluent Cloud resources needed for this tutorial and generate a Table API client configuration file. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent flink region list --cloud <CLOUD>`.

```shell
confluent quickstart \
    --region us-east-1 \
    --cloud aws \
    --environment-name flink_table_api_tutorials_environment \
    --kafka-cluster-name flink_table_api_tutorials_cluster \
    --compute-pool-name flink_table_api_tutorials_pool \
    --max-cfu 10 \
    --create-flink-key \
    --flink-properties-file ./flink-process-table-function/table-api-cc/src/main/resources/cloud.properties
```

The plugin should complete in under a minute and will generate a properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file).

## Inspect the PTF code

The `Median` class (located under `flink-process-table-function/median-ptf`) demonstrates a custom [Process Table Function (PTF)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html), Flink's most flexible user-defined function type that supports stateful transformations over table partitions. Because the PTF implementation relies on Java reflection, PTF developers must guide the Flink runtime by providing hint annotations:

* A class-level `@DataTypeHint` that specifies the PTF output schema. Since the median PTF is outputting a `<temperature, trailing median>` pair, the hint is `"ROW<temperature DOUBLE, median DOUBLE>"`
* A `@StateHint` `eval` method argument that gives access to partitioned state that you manage. Since we are implementing a median over trailing N readings per temperature sensor, the state class contains a list of temperatures.
* An `@ArgumentHint` on the input `Row` specifying whether we are implementing a stateless per-row PTF, or a per-partition stateful PTF that operates on a set of rows. A median over previous events requires set semantics.
* A `@DataTypeHint` on any PTF arguments. We make the PTF flexible with respect to the maximum number of previous events over which to calculate the median.

Due to the reflection-based implementation of PTFs in the Flink runtime, the PTF method to implement must be named `eval`. Developers must follow this naming convention; it's not enforced by an interface or abstract class method. The `Median` `eval` method maintains the list of trailing temperatures by adding the current row's temperature onto the end of the list and removing the oldest reading from the beginning if the list size surpasses the input `numTrailing` argument. Then it outputs the current temperature and trailing median by calling [`ProcessTableFunction.collect`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/functions/ProcessTableFunction.html#collect(T)):

```java
Double temperature = row.getFieldAs("temperature");

trailingTemps.temps.add(temperature);
while (trailingTemps.temps.size() > numTrailing) {
    trailingTemps.temps.remove(0);
}

collect(Row.of(temperature, Quantiles.median().compute(trailingTemps.temps)));
```

## Deploy the PTF

Now that we've examined the code, let's deploy the PTF to Confluent Cloud. First, build an uberjar containing all dependencies:

```shell
./gradlew flink-process-table-function:median-ptf:shadowJar
```

Upload the JAR as a Flink artifact:

```shell
confluent flink artifact create median_ptf \
    --artifact-file ./flink-process-table-function/median-ptf/build/libs/median-ptf-all.jar \
    --cloud aws \
    --region us-east-1
```

Take note of the artifact ID returned (it will look like `cfa-123456`). Next, open the Flink SQL shell:

```shell
confluent flink shell --cloud aws --region us-east-1
```

Set the active catalog and database to match your environment and cluster:

```shell
USE CATALOG flink_table_api_tutorials_environment;
USE flink_table_api_tutorials_cluster;
```

Finally, register the PTF as a function, replacing `cfa-123456` with your actual artifact ID:

```shell
CREATE FUNCTION Median
AS 'io.confluent.developer.Median'
USING JAR 'confluent-artifact://cfa-123456';
```

## Call the PTF with Flink SQL

With the PTF registered, let's try it out. First, create a table to hold temperature sensor readings:

```shell
CREATE TABLE temperature_readings (
  sensor_id INT,
  temperature DOUBLE,
  ts TIMESTAMP(3),
  WATERMARK FOR ts AS ts
);
```

Insert some sample temperature data:

```shell
INSERT INTO temperature_readings VALUES
(0, 55, TO_TIMESTAMP('2026-05-01 02:15:30')),
(0, 50, TO_TIMESTAMP('2026-05-01 02:20:30')),
(0, 45, TO_TIMESTAMP('2026-05-01 02:25:30')),
(0, 40, TO_TIMESTAMP('2026-05-01 02:30:30')),
(0, 45, TO_TIMESTAMP('2026-05-01 02:35:30')),
(0, 50, TO_TIMESTAMP('2026-05-01 02:40:30')),
(0, 55, TO_TIMESTAMP('2026-05-01 02:45:30')),
(0, 60, TO_TIMESTAMP('2026-05-01 02:50:30')),
(0, 60, TO_TIMESTAMP('2026-05-01 02:53:30'));
```

Now call the `Median` PTF, computing the median over the last 3 temperature readings per sensor:

```shell
SELECT *
FROM Median(TABLE temperature_readings PARTITION BY sensor_id, 3);
```

You should see output showing each temperature along with its trailing 3-event median:

```plaintext
sensor_id temperature median         
0         55.0        55.0           
0         50.0        52.5           
0         45.0        50.0           
0         40.0        45.0           
0         45.0        45.0           
0         50.0        45.0           
0         55.0        50.0           
0         60.0        55.0           
0         55.0        55.0           
0         60.0        60.0
```

## Call the PTF via the Table API

You can also call PTFs programmatically using Flink's Table API. The code in `TableApiPtfConfluentCloud.java` demonstrates this by creating an in-memory table of temperature readings and calling the `Median` function via [`Table.process`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/Table.html#process(java.lang.Class,java.lang.Object...)). Because our PTF requires set semantics, we must also first partition by the `sensor_id` field:

```java
TableResult tableResult = tableEnv.from("temperature_readings")
    .partitionBy($("sensor_id"))
    .process(Median.class,
            lit(3).asArgument("numTrailing"))
    .execute();
```

Compile the Table API application:

```shell
./gradlew flink-process-table-function:table-api-cc:build
```

Run it to see the median calculations in action:

```shell
./gradlew flink-process-table-function:table-api-cc:run
```

The application will print the first five median calculations:

```noformat
Current temp: 55.0, median over last 3: 55.0
Current temp: 50.0, median over last 3: 52.5
Current temp: 45.0, median over last 3: 50.0
Current temp: 40.0, median over last 3: 45.0
Current temp: 45.0, median over last 3: 45.0
```

## Tear down Confluent Cloud infrastructure

When you are done, be sure to clean up any Confluent Cloud resources created for this tutorial. Since you created all resources in a Confluent Cloud environment, you can simply delete the environment and most of the resources will be deleted (e.g., the Kafka cluster and Flink compute pool). Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `flink_table_api_tutorials_environment`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

Next, delete the Flink API key. This API key isn't associated with the deleted environment, so it needs to be deleted separately. Find the key:

```shell
confluent api-key list --resource flink --current-user
```

And then copy the 16-character alphanumeric key and delete it:
```shell
confluent api-key delete <KEY>
```

Finally, for the sake of housekeeping, delete the Table API client configuration file:

```shell
rm flink-process-table-function/table-api-cc/src/main/resources/cloud.properties
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

  The `Median` class (located under `flink-process-table-function/median-ptf`) demonstrates a custom [Process Table Function (PTF)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html), Flink's most flexible user-defined function type that supports stateful transformations over table partitions. Because the PTF implementation relies on Java reflection, PTF developers must guide the Flink runtime by providing hint annotations:

  * A class-level `@DataTypeHint` that specifies the PTF output schema. Since the median PTF is outputting a `<temperature, trailing median>` pair, the hint is `"ROW<temperature DOUBLE, median DOUBLE>"`
  * A `@StateHint` on the `eval` method argument that gives access to partitioned state that you manage. Since we are implementing a median over trailing N readings per temperature sensor, the state class contains a list of temperatures.
  * An `@ArgumentHint` on the input `Row` specifying whether we are implementing a stateless per-row PTF, or a per-partition stateful PTF that operates on a set of rows. A median over previous events requires set semantics.
  * A `@DataTypeHint` on any PTF arguments. We make the PTF flexible with respect to the maximum number of previous events over which to calculate the median.

  Due to the reflection-based implementation of PTFs in the Flink runtime, the PTF method to implement must be named `eval`. Developers must follow this naming convention; it's not enforced by an interface or abstract class method. The `Median` `eval` method maintains the list of trailing temperatures by adding the current row's temperature onto the end of the list and removing the oldest reading from the beginning if the list size surpasses the input `numTrailing` argument. Then it outputs the current temperature and trailing median by calling [`ProcessTableFunction.collect`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/functions/ProcessTableFunction.html#collect(T)):

  ```java
  Double temperature = row.getFieldAs("temperature");

  trailingTemps.temps.add(temperature);
  while (trailingTemps.temps.size() > numTrailing) {
      trailingTemps.temps.remove(0);
  }

  collect(Row.of(temperature, Quantiles.median().compute(trailingTemps.temps)));
  ```

  ## Deploy the PTF

  Now that we've examined the code, let's deploy the PTF to your local Flink environment. First, compile the PTF into an uberjar:

  ```shell
  ./gradlew flink-process-table-function:median-ptf:shadowJar
  ```

  Copy the JAR into the Flink SQL client container:

  ```shell
  docker cp flink-process-table-function/median-ptf/build/libs/median-ptf-all.jar flink-sql-client:/opt/flink/lib
  ```

  Open a Flink SQL shell:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Once in the SQL shell, load the JAR file:

  ```shell
  ADD JAR '/opt/flink/lib/median-ptf-all.jar';
  ```

  Register the PTF as a function:

  ```shell
  CREATE FUNCTION Median
  AS 'io.confluent.developer.Median'
  USING JAR '/opt/flink/lib/median-ptf-all.jar';
  ```

  ## Call the PTF with Flink SQL

  With the PTF registered, let's try it out. First, create a Kafka-backed table to hold temperature sensor readings:

  ```shell
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      ts TIMESTAMP(3),
      `partition` BIGINT METADATA VIRTUAL,
      `offset` BIGINT METADATA VIRTUAL,
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

  Insert some sample temperature readings:

  ```shell
  INSERT INTO temperature_readings VALUES
      (0, 55, TO_TIMESTAMP('2026-05-01 02:15:30')),
      (0, 50, TO_TIMESTAMP('2026-05-01 02:20:30')),
      (0, 45, TO_TIMESTAMP('2026-05-01 02:25:30')),
      (0, 40, TO_TIMESTAMP('2026-05-01 02:30:30')),
      (0, 45, TO_TIMESTAMP('2026-05-01 02:35:30')),
      (0, 50, TO_TIMESTAMP('2026-05-01 02:40:30')),
      (0, 55, TO_TIMESTAMP('2026-05-01 02:45:30')),
      (0, 60, TO_TIMESTAMP('2026-05-01 02:50:30')),
      (0, 60, TO_TIMESTAMP('2026-05-01 02:53:30'));
  ```

  Now call the `Median` PTF, computing the median over the last 3 temperature readings per sensor:

  ```shell
  SELECT *
  FROM Median(TABLE temperature_readings PARTITION BY sensor_id, 3);
  ```

  You should see output showing each temperature along with its trailing 3-event median:

  ```plaintext
  sensor_id temperature median         
  0         55.0        55.0           
  0         50.0        52.5           
  0         45.0        50.0           
  0         40.0        45.0           
  0         45.0        45.0           
  0         50.0        45.0           
  0         55.0        50.0           
  0         60.0        55.0           
  0         55.0        55.0           
  0         60.0        60.0
  ```

  ## Call the PTF via the Table API

  You can also call PTFs programmatically using Flink's Table API. The code in `TableApiPtfLocal.java` demonstrates this by creating an in-memory table of temperature readings and calling the `Median` function via [`Table.process`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/Table.html#process(java.lang.Class,java.lang.Object...)). Because our PTF requires set semantics, we must also first partition by the `sensor_id` field:

  ```java
  TableResult tableResult = tableEnv.from("temperature_readings")
      .partitionBy($("sensor_id"))
      .process(Median.class,
              lit(3).asArgument("numTrailing"))
      .execute();
  ```

  Compile the Table API application:

  ```shell
  ./gradlew flink-process-table-function:table-api-oss:build
  ```

  Run it to see the median calculations in action:

  ```shell
  ./gradlew flink-process-table-function:table-api-oss:run
  ```

  The application will print the first five median calculations:

  ```noformat
  Current temp: 55.0, median over last 3: 55.0
  Current temp: 50.0, median over last 3: 52.5
  Current temp: 45.0, median over last 3: 50.0
  Current temp: 40.0, median over last 3: 45.0
  Current temp: 45.0, median over last 3: 45.0
  ```

  ## Clean up

  From your local machine, stop the Kafka, Schema Registry, and Flink containers:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml down
  ```
</details>
