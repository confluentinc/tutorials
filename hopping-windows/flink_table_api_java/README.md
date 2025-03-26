<!-- title: How to aggregate Kafka messages over sliding or hopping windows in Java using Flink's Table API for Confluent Cloud -->
<!-- description: In this tutorial, learn how to aggregate Kafka messages over sliding or hopping windows in Java using Flink's Table API for Confluent Cloud, with step-by-step instructions and supporting code. -->

# How to aggregate Kafka messages over sliding or hopping windows in Java using Flink's Table API for Confluent Cloud

In this tutorial, you will learn how to aggregate messages over sliding or hopping windows. This kind of window is fixed-size with an advance that may be smaller than the window size (unlike tumbling windows, where the advance equals the window size). Due to that fact that the advance is smaller than the window size, hopping windows contains overlapping results (i.e., the same event can be included in multiple consecutive hopping windows).

Note that Flink refers to these windows as either sliding or hopping windows depending on the API: in Flink SQL the relevant windowing table-valued function is [`HOP`](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql/queries/window-tvf/#hop), while in the Table API you construct a sliding window object fluently with the [`Slide`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/Slide.html) class.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* Java 21, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. Validate that `java -version` shows version 21.
* Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

## Provision Confluent Cloud infrastructure

If you already have the Confluent Cloud resources required to populate a Table API client configuration file, e.g., from running a different tutorial, you may skip to the [next step](#inspect-the-code) after creating or copying the properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file) to `hopping-windows/flink_table_api_java/src/main/resources/cloud.properties` within the top-level `tutorials` directory.

If you need to create the Confluent Cloud infrastructure needed to run this tutorial, the `confluent-flink-quickstart` CLI plugin creates the resources that you need to get started with Confluent Cloud for Apache Flink. Install it by running:

```shell
confluent plugin install confluent-flink-quickstart
```

Run the plugin as follows to create the Confluent Cloud resources needed for this tutorial and generate a Table API client configuration file. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent flink region list --cloud <CLOUD>`.

```shell
confluent flink quickstart \
    --name flink_table_api_tutorials \
    --max-cfu 10 \
    --region us-east-1 \
    --cloud aws \
    --table-api-client-config-file ./hopping-windows/flink_table_api_java/src/main/resources/cloud.properties
```

The plugin should complete in under a minute and will generate a properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file).

## Inspect the code

### Dependencies

Before digging into Java source code, first check out the two dependencies required to use the Flink Table API for Confluent Cloud. These are defined in the `dependencies` section of the `hopping-windows/flink_table_api_java/build.gradle` file. (For Maven, see the analogous `pom.xml` snippet [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#add-the-table-api-to-an-existing-java-project).)

* `org.apache.flink:flink-table-api-java`: This is the Apache Flink Table API implementation dependency. It contains, for example, the classes implementing the Table API DSL (domain-specific language).
* `io.confluent.flink:confluent-flink-table-api-java-plugin`: This dependency contains the "glue" for instantiating an Apache Flink Table API table environment against Confluent Cloud (i.e., an implementation of the [`org.apache.flink.table.api.TableEnvironment`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/TableEnvironment.html) interface), as well as other helper utilities that we will use in this tutorial.

### Java source

Take a look at the source code in `hopping-windows/flink_table_api_java/FlinkTableApiHoppingWindows.java`. These two lines instantiate a table environment for executing Table API programs against Confluent Cloud:

```java
EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
TableEnvironment tableEnv = TableEnvironment.create(envSettings);
```

Let's aggregate one of Confluent Cloud's example tables. You can find these tables in the read-only `marketplace` database of the `examples` catalog. The source code in this example uses the Table API's [`Table.window`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/Table.html#window-org.apache.flink.table.api.GroupWindow-) and [`GroupWindowedTable.groupBy`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/GroupWindowedTable.html) methods to aggregate over 2 second windows that advance every second. The aggregation is a simple `count()`.

```java
TableResult tableResult = tableEnv.from("examples.marketplace.orders")
    .window(
            Slide.over(lit(2).seconds())
                    .every(lit(1).seconds())
                    .on($("$rowtime"))
                    .as("window")
    ).groupBy(
            $("window")
    ).select(
            $("customer_id").count().as("count"),
            $("window").start().as("window_start"),
            $("window").end().as("window_end")
    )
    .execute();
```

Given the table result, we can then materialize (in memory) the rows in the resulting stream by calling [`ConfluentTools.collectMaterialized`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized) or [`ConfluentTools.printMaterialized`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized). This line materializes and prints 2 rows from the table result:

```java
ConfluentTools.printMaterialized(tableResult, 2);
```

Alternatively, we can use the Table API's [`TableResult`](https://docs.confluent.io/cloud/current/flink/reference/functions/table-api-functions.html#tableresult-interface) interface directly to collect rows. For example, to print the count for one window:

```java
try (CloseableIterator<Row> it = tableResult.collect()) {
    if (it.hasNext()) {
        Row row = it.next();
        System.out.println(row.getField("count"));
        System.out.println(row.getField("window_start"));
        System.out.println(row.getField("window_end"));
    }
}
```

## Run the program

You can run the example program directly in your IDE by opening the Gradle project located at `hopping-windows/flink_table_api_java/`, or via the command line from the top-level `tutorials` directory:

```shell
./gradlew hopping-windows:flink_table_api_java:run
```

The program will output 2 rows materialized via `printMaterialized`, and then an additional count and window start and end. Note that the same `TableResult` (and its underlying iterator) is used, so the last windows that is printed comes right after the first two windows printed. The output will look like this:

```noformat
+-------+-------------------------+-------------------------+
| count |            window_start |              window_end |
+-------+-------------------------+-------------------------+
|    90 | 2025-03-18 09:42:58.000 | 2025-03-18 09:43:00.000 |
|   141 | 2025-03-18 09:42:59.000 | 2025-03-18 09:43:01.000 |
+-------+-------------------------+-------------------------+
2 rows in set
102
2025-03-18T09:43
2025-03-18T09:43:02
```

## Tear down Confluent Cloud infrastructure

When you are done, be sure to clean up any Confluent Cloud resources created for this tutorial. Since you created all resources in a Confluent Cloud environment, you can simply delete the environment and most of the resources created for this tutorial (e.g., the Kafka cluster and Flink compute pool) will be deleted. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `flink_table_api_tutorials_environment`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

Next, delete the Flink API key. This API key isn't associated with the deleted environment so it needs to be deleted separately. Find the key:

```shell
confluent api-key list --resource flink --current-user
```

And then copy the 16-character alphanumeric key and delete it:
```shell
confluent api-key delete <KEY>
```

Finally, for the sake of housekeeping, delete the Table API client configuration file:

```shell
rm hopping-windows/flink_table_api_java/src/main/resources/cloud.properties
```
