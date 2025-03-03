<!-- title: How to filter Kafka messages in Python using Flink's Table API for Confluent Cloud -->
<!-- description: In this tutorial, learn how to filter Kafka messages in Python using Flink's Table API for Confluent Cloud, with step-by-step instructions and supporting code. -->

# How to filter Kafka messages in Python using Flink's Table API for Confluent Cloud

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

If you already have the Confluent Cloud resources required to populate a Table API client configuration file, e.g., from running a different tutorial, you may skip to the [next step](#inspect-the-code) after creating or copying the properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file) to `filtering/flink_table_api_python/cloud.properties` within the top-level `tutorials` directory.

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
    --table-api-client-config-file ./filtering/flink_table_api_python/cloud.properties
```

The plugin should complete in under a minute and will generate a properties file as documented [here](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#properties-file).

## Inspect the code

Take a look at the source code in `filtering/flink_table_api_python/filtering.py`. These two lines instantiate a table environment for executing Table API programs against Confluent Cloud:

```python
settings = ConfluentSettings.from_file("./cloud.properties")
env = TableEnvironment.create(settings)
```

Let's filter one of Confluent Cloud's example tables. You can find these tables in the read-only `marketplace` database of the `examples` catalog. The source code in this example uses the Table API's [`Table.filter`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/api/pyflink.table.Table.filter.html#pyflink.table.Table.filter) method to find orders greater than or equal to 50 (we also could have used the equivalent [`Table.where`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/api/pyflink.table.Table.where.html#pyflink.table.Table.where) method):

```python
table_result = env.from_path("examples.marketplace.orders") \
    .select(col("customer_id"), col("product_id"), col("price")) \
    .filter(col("price") >= 50) \
    .execute()
```

Given the table result, we can then materialize (in memory) the rows in the resulting stream by calling [`ConfluentTools.collect_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized) or [`ConfluentTools.print_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized). This line materializes and prints 5 rows from the table result:

```python
ConfluentTools.print_materialized_limit(table_result, 5)
```

Alternatively, we can use the Table API's [`TableResult`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/statement_set.html#tableresult) interface directly to collect rows. For example, to print the price of 5 orders:

```python
with table_result.collect() as rows:
    i = 0
    for row in rows:
        print(row[2])
        i += 1
        if i >= 5: break
```

## Run the program

In order to run the program, first create a Python virtual environment in which to install the required dependencies. E.g., run the following commands to use the `venv` module. _Note: use `python3` and `pip3` in the following commands if `python` and `pip` refer to Python 2 on your system._

```shell
cd filtering/flink_table_api_python/
python -m venv venv; source ./venv/bin/activate;
```

Install the `confluent-flink-table-api-python-plugin` package:

```shell
pip install confluent-flink-table-api-python-plugin
```

You can run the example program directly in your IDE by opening the project located at `filtering/flink_table_api_python/`, or via the command line:

```shell
python filtering.py
```

The program will output 5 rows materialized via [`print_materialized_limit`](https://docs.confluent.io/cloud/current/flink/reference/table-api.html#confluenttools-collect-materialized-and-confluenttools-print-materialized), and then 5 prices from iterating over the table result. Note that the same [`TableResult`](https://nightlies.apache.org/flink/flink-docs-stable/api/python/reference/pyflink.table/statement_set.html#tableresult) (and its underlying iterator) is used, so the first five prices won't match the last five prices. The output will look like this:

```noformat
+-------------+------------+-------+
| customer_id | product_id | price |
+-------------+------------+-------+
|        3217 |       1262 | 50.87 |
|        3151 |       1048 | 52.68 |
|        3208 |       1256 | 89.98 |
|        3085 |       1336 |  57.0 |
|        3124 |       1489 | 96.04 |
+-------------+------------+-------+
5 rows in set
57.32
83.16
56.64
82.71
79.66
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
rm filtering/flink_table_api_python/cloud.properties
```
