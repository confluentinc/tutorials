<!-- title: How to join two streams of data in Python using Flink's Table API for Confluent Cloud -->
<!-- description: In this tutorial, learn how to join two streams of data in Java using Flink's Table API for Confluent Cloud, with step-by-step instructions and supporting code. -->

# How to join two streams of data in Java using Flink's Table API for Confluent Cloud

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

If you already have the Confluent Cloud resources required to populate a Table API client configuration file, e.g., from running a different tutorial, you may skip to the [next step](#inspect-the-code) after creating or copying the properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file) to `joining-stream-stream/flink_table_api_python/cloud.properties` within the top-level `tutorials` directory.

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
    --table-api-client-config-file ./joining-stream-stream/flink_table_api_python/cloud.properties
```

The plugin should complete in under a minute and will generate a properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file).

## Inspect the code

Take a look at the source code in `joining-stream-stream/flink_table_api_python/joining-stream-stream.py`. These two lines instantiate a table environment for executing Table API programs against Confluent Cloud:

```python
settings = ConfluentSettings.from_file("./cloud.properties")
env = TableEnvironment.create(settings)
```

Let's join two of Confluent Cloud's example tables: `orders` and `customers`. You can find these tables in the read-only `marketplace` database of the `examples` catalog. The source code in this example uses the Table API's [`Table.join`](hhttps://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/api/pyflink.table.Table.join.html#pyflink.table.Table.join) method to join these tables on the common `customer_id` key. Note that we must rename one table's `customer_id` field since the field names of the two joined tables can't overlap. We also add a condition that the row time of the order must be greater than or equal to the row time of the customer row.

```python
table_result = orders_table \
    .join(customers_table, col('order_time') >= col('customer_time')
                           and col('order_customer_id') == col('customer_id')) \
    .select(
        col('order_id'),
        col('product_id'),
        col('name'),
        col('order_time'),
        col('customer_time')
    ) \
    .execute()
```

Given the table result, we can then materialize (in memory) the rows in the resulting stream by calling [`ConfluentTools.collect_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized) or [`ConfluentTools.print_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized). This line materializes and prints 5 rows from the table result:

```python
ConfluentTools.print_materialized_limit(table_result, 5)
```

## Run the program

In order to run the program, first create a Python virtual environment in which to install the required dependencies. E.g., run the following commands to use the `venv` module. _Note: use `python3` and `pip3` in the following commands if `python` and `pip` refer to Python 2 on your system._

```shell
cd joining-stream-stream/flink_table_api_python/
python -m venv venv; source ./venv/bin/activate;
```

Install the `confluent-flink-table-api-python-plugin` package:

```shell
pip install confluent-flink-table-api-python-plugin
```

You can run the example program directly in your IDE by opening the project located at `joining-stream-stream/flink_table_api_python/`, or via the command line:

```shell
python joining-stream-stream.py
```

The program will output 5 rows materialized via [`print_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized). The output will look like this:

```noformat
+--------------------------------+------------+----------------------+-------------------------+-------------------------+
|                       order_id | product_id |                 name |              order_time |           customer_time |
+--------------------------------+------------+----------------------+-------------------------+-------------------------+
| ba98cd10-e1fc-45cd-99d1-e06... |       1220 |        Alonso Stokes | 2025-03-18 11:37:14.194 | 2025-03-18 11:37:13.698 |
| abdcc320-7d8c-47f7-970d-bba... |       1381 |         Emilia Huels | 2025-03-18 11:37:13.935 | 2025-03-18 11:37:13.698 |
| cfb0147c-5ce1-4bf9-8bd9-7ca... |       1196 | Miss Stephan Ruecker | 2025-03-18 11:37:13.895 | 2025-03-18 11:37:13.796 |
| 36f1e707-fed4-4c8e-92a5-b55... |       1365 | Miss Stephan Ruecker | 2025-03-18 11:37:13.995 | 2025-03-18 11:37:13.796 |
| 109dd8f8-293b-4a98-a972-fea... |       1393 |        Alease Russel | 2025-03-18 11:37:13.697 | 2025-03-18 11:37:13.994 |
+--------------------------------+------------+----------------------+-------------------------+-------------------------+
5 rows in set
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
rm joining-stream-stream/flink_table_api_python/cloud.properties
```
