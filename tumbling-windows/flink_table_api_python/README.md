<!-- title: How to aggregate Kafka messages over tumbling windows in Python using Flink's Table API for Confluent Cloud -->
<!-- description: In this tutorial, learn how to aggregate Kafka messages over tumbling windows in Python using Flink's Table API for Confluent Cloud, with step-by-step instructions and supporting code. -->

# How to aggregate Kafka messages over tumbling windows in Python using Flink's Table API for Confluent Cloud

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* [Python](https://www.python.org/downloads/) 3.8 or later
* Java 21, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. Validate that `java -version` shows version 21. _Note: Java is required since the Flink Python API uses [Py4J](https://www.py4j.org/) to communicate with the JVM under the hood._
* Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

## Provision Confluent Cloud infrastructure

If you already have the Confluent Cloud resources required to populate a Table API client configuration file, e.g., from running a different tutorial, you may skip to the [next step](#inspect-the-code) after creating or copying the properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file) to `tumbling-windows/flink_table_api_python/cloud.properties` within the top-level `tutorials` directory.

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
    --table-api-client-config-file ./tumbling-windows/flink_table_api_python/cloud.properties
```

The plugin should complete in under a minute and will generate a properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file).

## Inspect the code

Take a look at the source code in `tumbling-windows/flink_table_api_python/tumbling-windows.py`. These two lines instantiate a table environment for executing Table API programs against Confluent Cloud:

```python
settings = ConfluentSettings.from_file("./cloud.properties")
env = TableEnvironment.create(settings)
```

Let's aggregate one of Confluent Cloud's example tables. You can find these tables in the read-only `marketplace` database of the `examples` catalog. The source code in this example uses the Table API's [`Table.window`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/api/pyflink.table.Table.window.html#pyflink.table.Table.window) (over a [`Tumble`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/window.html#tumble-window) window) and [`GroupWindowedTable.group_by`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/api/pyflink.table.GroupWindowedTable.group_by.html#pyflink.table.GroupWindowedTable.group_by) methods to aggregate over 2 second windows. The aggregation is a simple `count`.

```python
table_result = env.from_path("examples.marketplace.orders") \
    .window(
        Tumble.over(lit(2).seconds)
            .on(col('$rowtime'))
            .alias('window')
    ) \
    .group_by(col('window')) \
    .select(
        col('customer_id').count.alias('count'),
        col('window').start.alias('window_start'),
        col('window').end.alias('window_end')
    ) \
    .execute()
```

Given the table result, we can then materialize (in memory) the rows in the resulting stream by calling [`ConfluentTools.collect_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized) or [`ConfluentTools.print_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized). This line materializes and prints 2 rows from the table result:

```python
ConfluentTools.print_materialized_limit(table_result, 2)
```

Alternatively, we can use the Table API's [`TableResult`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/statement_set.html#tableresult) interface directly to collect rows. For example, to print the next two windows:

```python
with table_result.collect() as rows:
    i = 0
    for row in rows:
        print(f"count: {row[0]}, window start: {row[1]}, window end: {row[2]}")
        i += 1
        if i >= 2: break
```

## Run the program

In order to run the program, first create a Python virtual environment in which to install the required dependencies. E.g., run the following commands to use the `venv` module. _Note: use `python3` and `pip3` in the following commands if `python` and `pip` refer to Python 2 on your system._

```shell
cd tumbling-windows/flink_table_api_python/
python -m venv venv; source ./venv/bin/activate;
```

Install the `confluent-flink-table-api-python-plugin` package:

```shell
pip install confluent-flink-table-api-python-plugin
```

You can run the example program directly in your IDE by opening the project located at `tumbling-windows/flink_table_api_python/`, or via the command line:

```shell
python tumbling-windows.py
```

The program will output 2 rows materialized via [`print_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized), and then the next window's count directly via the table result. Note that the same [`TableResult`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/statement_set.html#tableresult) (and its underlying iterator) is used, so the last window that is printed comes right after the first two windows printed. The output will look like this:

```noformat
+-------+-------------------------+-------------------------+
| count |            window_start |              window_end |
+-------+-------------------------+-------------------------+
|    31 | 2025-03-03 11:23:14.000 | 2025-03-03 11:23:16.000 |
|   103 | 2025-03-03 11:23:16.000 | 2025-03-03 11:23:18.000 |
+-------+-------------------------+-------------------------+
2 rows in set
count: 100, window start: 2025-03-03 11:23:18, window end: 2025-03-03 11:23:20
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
rm tumbling-windows/flink_table_api_python/cloud.properties
```
