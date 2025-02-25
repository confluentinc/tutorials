<!-- title: How to filter Kafka messages in Java using Flink's Table API for Confluent Cloud -->
<!-- description: In this tutorial, learn how to filter Kafka messages in Java using Flink's Table API for Confluent Cloud, with step-by-step instructions and supporting code. -->

# How to filter Kafka messages in Java using Flink's Table API for Confluent Cloud

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

If you already have the Confluent Cloud resources required to populate a Table API client configuration file, e.g., from running a different tutorial, you may skip to the [next step](#inspect-the-code) after creating or copying the properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file) to `filtering/flink_table_api_java/src/main/resources/cloud.properties` within the top-level `tutorials` directory.

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
    --table-api-client-config-file ./filtering/flink_table_api_java/src/main/resources/cloud.properties
```

The plugin should complete in under a minute and will generate a properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file).

## Inspect the code

### Dependencies

Before digging into Java source code, first check out the two dependencies required to use the Flink Table API for Confluent Cloud. These are defined in the `dependencies` section of the `filtering/flink_table_api_java/build.gradle` file. (For Maven, see the analogous `pom.xml` snippet [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#add-the-table-api-to-an-existing-java-project).)

* `org.apache.flink:flink-table-api-java`: This is the Apache Flink Table API implementation dependency. It contains, for example, the classes implementing the Table API DSL (domain-specific language).
* `io.confluent.flink:confluent-flink-table-api-java-plugin`: This dependency contains the "glue" for instantiating an Apache Flink Table API table environment against Confluent Cloud (i.e., an implementation of the [`org.apache.flink.table.api.TableEnvironment`](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/table/api/TableEnvironment.html) interface), as well as other helper utilities that we will use in this tutorial.

### Java source

Take a look at the source code in `filtering/flink_table_api_java/FlinkTableApiFiltering.java`. These two lines instantiate a table environment for executing Table API programs against Confluent Cloud:

```java
EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
TableEnvironment tableEnv = TableEnvironment.create(envSettings);
```

Let's filter one of Confluent Cloud's example tables. You can find these tables in the read-only `marketplace` database of the `examples` catalog. The source code in this example uses the Table API's [`Table.filter`](https://docs.confluent.io/cloud/current/flink/reference/functions/table-api-functions.html#table-interface-sql-equivalents) method to find orders greater than or equal to 50 (we also could have used the equivalent [`Table.where`](https://docs.confluent.io/cloud/current/flink/reference/functions/table-api-functions.html#table-interface-sql-equivalents) method):

```java
TableResult tableResult = tableEnv.from("examples.marketplace.orders")
    .select($("customer_id"), $("product_id"), $("price"))
    .filter($("price").isGreaterOrEqual(50))
    .execute();
```

Given the table result, we can then materialize (in memory) the rows in the resulting stream by calling [`ConfluentTools.collectMaterialized`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized) or [`ConfluentTools.printMaterialized`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized). This line materializes and prints 5 rows from the table:

```java
ConfluentTools.printMaterialized(tableResult, 5);
```

Alternatively, we can use the Table API's [`TableResult`](https://docs.confluent.io/cloud/current/flink/reference/functions/table-api-functions.html#tableresult-interface) interface directly to collect rows. For example, to print the price of 5 orders:

```java
try (CloseableIterator<Row> it = tableResult.collect()) {
    for (int i = 0; it.hasNext() && i < 5; i++) {
        Row row = it.next();
        System.out.println(row.getField("price"));
    }
}
```

## Run the program

You can run the example program directly in your IDE by opening the Gradle project located at `filtering/flink_table_api_java/`, or via the command line from the top-level `tutorials` directory:

```shell
./gradlew filtering:flink_table_api:java:run
```

The program will output 5 rows materialized via `printMaterialized`, and then 5 prices from iterating over the table result. Note that the same `TableResult` (and its underlying iterator) is used, so the first five prices won't match the last five prices. The output will look like this:

```noformat
+-------------+------------+-------+
| customer_id | product_id | price |
+-------------+------------+-------+
|        3028 |       1360 | 53.94 |
|        3078 |       1498 | 52.58 |
|        3241 |       1280 | 90.87 |
|        3074 |       1390 | 58.26 |
|        3043 |       1269 | 80.69 |
+-------------+------------+-------+
5 rows in set
99.33
88.68
53.22
91.92
87.07
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
rm filtering/flink_table_api_java/src/main/resources/cloud.properties
```
