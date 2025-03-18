<!-- title: How to join two streams of data in Java using Flink's Table API for Confluent Cloud -->
<!-- description: In this tutorial, learn how to join two streams of data in Java using Flink's Table API for Confluent Cloud, with step-by-step instructions and supporting code. -->

# How to join two streams of data in Java using Flink's Table API for Confluent Cloud

In this tutorial, you will learn how to join streams of data using the Flink Table API. We will use a [regular join](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql/queries/joins/#regular-joins) to demonstrate this, specifically an [inner equi-join](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/sql/queries/joins/#inner-equi-join) that has an equality predicate.

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

If you already have the Confluent Cloud resources required to populate a Table API client configuration file, e.g., from running a different tutorial, you may skip to the [next step](#inspect-the-code) after creating or copying the properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file) to `joining-stream-stream/flink_table_api_java/src/main/resources/cloud.properties` within the top-level `tutorials` directory.

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
    --table-api-client-config-file ./joining-stream-stream/flink_table_api_java/src/main/resources/cloud.properties
```

The plugin should complete in under a minute and will generate a properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file).

## Inspect the code

### Dependencies

Before digging into Java source code, first check out the two dependencies required to use the Flink Table API for Confluent Cloud. These are defined in the `dependencies` section of the `joining-stream-stream/flink_table_api_java/build.gradle` file. (For Maven, see the analogous `pom.xml` snippet [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#add-the-table-api-to-an-existing-java-project).)

* `org.apache.flink:flink-table-api-java`: This is the Apache Flink Table API implementation dependency. It contains, for example, the classes implementing the Table API DSL (domain-specific language).
* `io.confluent.flink:confluent-flink-table-api-java-plugin`: This dependency contains the "glue" for instantiating an Apache Flink Table API table environment against Confluent Cloud (i.e., an implementation of the [`org.apache.flink.table.api.TableEnvironment`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/TableEnvironment.html) interface), as well as other helper utilities that we will use in this tutorial.

### Java source

Take a look at the source code in `joining-stream-stream/flink_table_api_java/FlinkTableApiJoin.java`. These two lines instantiate a table environment for executing Table API programs against Confluent Cloud:

```java
EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
TableEnvironment tableEnv = TableEnvironment.create(envSettings);
```

Let's join two of Confluent Cloud's example tables: `orders` and `customers`. You can find these tables in the read-only `marketplace` database of the `examples` catalog. The source code in this example uses the Table API's [`Table.join`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/Table.html#join-org.apache.flink.table.api.Table-) and [`Table.where`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/Table.html#where-org.apache.flink.table.expressions.Expression-) methods to join these tables on the common `customer_id` key. Note that we must rename one table's `customer_id` field since the field names of the two joined tables can't overlap. We also add a condition that the row time of the order must be greater than or equal to the row time of the customer row.

```java
TableResult tableResult = ordersTable.join(customersTable)
    .where(
        and(
            $("order_customer_id").isEqual($("customer_id")),
            $("order_time").isGreaterOrEqual($("customer_time"))
        )
    )
    .select(
        $("order_id"),
        $("product_id"),
        $("name"),
        $("order_time"),
        $("customer_time")
    )
    .execute();
```

Given the table result, we can then materialize (in memory) the rows in the resulting stream by calling [`ConfluentTools.collectMaterialized`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized) or [`ConfluentTools.printMaterialized`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized). This line materializes and prints 5 rows from the table result:

```java
ConfluentTools.printMaterialized(tableResult, 5);
```

Alternatively, we can use the Table API's [`TableResult`](https://docs.confluent.io/cloud/current/flink/reference/functions/table-api-functions.html#tableresult-interface) interface directly to collect rows. For example, to print an additional join result:

```java
try (CloseableIterator<Row> it = tableResult.collect()) {
    if (it.hasNext()) {
        Row row = it.next();
        System.out.printf("Order %s, Name %s, Order time %s",
                          row.getField("order_id"), row.getField("name"), row.getField("order_time"));
    }
}
```

## Run the program

You can run the example program directly in your IDE by opening the Gradle project located at `joining-stream-stream/flink_table_api_java/`, or via the command line from the top-level `tutorials` directory:

```shell
./gradlew joining-stream-stream:flink_table_api_java:run
```

The program will output 5 rows materialized via `printMaterialized`, and then an additional join result. Note that the same `TableResult` (and its underlying iterator) is used, so the last result will differ from the first 5. The output will look like this:

```noformat
+--------------------------------+------------+-------------------+-------------------------+-------------------------+
|                       order_id | product_id |              name |              order_time |           customer_time |
+--------------------------------+------------+-------------------+-------------------------+-------------------------+
| 931118ab-287d-4428-a3d2-8bc... |       1350 | Dr. Frank Ullrich | 2025-03-18 10:57:40.741 | 2025-03-18 10:57:40.042 |
| 87710e48-e958-420d-b546-f82... |       1104 | Mrs. Morris Towne | 2025-03-18 10:57:40.561 | 2025-03-18 10:57:40.044 |
| 27b74fa4-61ef-4e7e-bd9b-142... |       1438 | Mrs. Morris Towne | 2025-03-18 10:57:41.141 | 2025-03-18 10:57:40.044 |
| 2a447e48-bccc-4fb9-b3f5-eac... |       1284 |      Ervin Cronin | 2025-03-18 10:57:40.641 | 2025-03-18 10:57:40.044 |
| 3ad33f2d-7a08-431f-9188-1dd... |       1442 |      Ervin Cronin | 2025-03-18 10:57:41.841 | 2025-03-18 10:57:40.044 |
+--------------------------------+------------+-------------------+-------------------------+-------------------------+
5 rows in set
Order 0733e723-532a-4aac-a534-c062331b23aa, Name Amberly Hessel, Order time 2025-03-18T14:57:40.541Z
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
rm joining-stream-stream/flink_table_api_java/src/main/resources/cloud.properties
```
