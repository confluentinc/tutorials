<!-- title: Streamhouse for Analytics Part 2 of 3: Query Iceberg with Spark -->
<!-- description: In this tutorial, learn how to query a Tableflow-synced Iceberg table with Spark, with step-by-step instructions and supporting code. -->

# Streamhouse for Analytics Part 2 of 3: Query Iceberg with Spark

In [Part 1](https://developer.confluent.io/confluent-tutorials/analytics-tableflow) of this series, you enabled Tableflow on a Kafka topic, which continuously materializes it as an [Apache Iceberg™](https://iceberg.apache.org/) table. In this tutorial, you'll query that table from Spark using Tableflow's built-in Iceberg REST Catalog. There's no export step, second database, or sync job to maintain. Spark reads the same data that backs your Kafka topic.

The REST Catalog works with any Iceberg-compatible query engine. This tutorial uses Spark because it runs in a local Docker container, but the same catalog configuration applies to systems like [Snowflake](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-snowflake.html), [Trino](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-trino.html), [Amazon Athena](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-aws.html), and [DuckDB](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-duckdb.html).

> **How this tutorial relates to [Streamhouse](https://streamhouse.com/):** Streamhouse is an open, vendor-neutral data architecture that connects streaming and table-based systems so data stays current wherever it's consumed. Querying the Iceberg table directly from Spark, with no export step or second database to keep in sync, is what that looks like on the analytics side: your query engine reads the same operational data that flows through your Kafka topic, kept fresh automatically.

## Prerequisites

* Completion of [Part 1](https://developer.confluent.io/confluent-tutorials/analytics-tableflow/) of this tutorial series, with the Datagen connector and Tableflow left running
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Verify that `docker compose version` succeeds.
* Navigate into the `analytics-query-iceberg` directory of your `confluentinc/tutorials` clone:
  ```shell
  cd analytics-query-iceberg
  ```

## Step 1: Set up access to the Iceberg REST Catalog

To query Tableflow's Iceberg tables, you need the REST Catalog endpoint and an API key scoped to Tableflow.

Get the REST Catalog endpoint:

1. In the [Confluent Cloud Console](https://confluent.cloud), navigate to your cluster and click **Tableflow** in the left navigation to open the Tableflow overview page.
2. In the **API access** section, copy the **REST Catalog Endpoint**. It looks like the following, with your cluster's cloud and region in place of `aws` and `us-east-1`:
   ```text
   https://tableflow.us-east-1.aws.confluent.cloud/iceberg/catalog/organizations/{ORG_ID}/environments/{ENV_ID}
   ```

Create an API key scoped to Tableflow:

```shell
confluent api-key create --resource tableflow
```

Save the key and secret. The notebook in Step 3 prompts for them, so you never write them into the notebook itself.

## Step 2: Start Spark and Jupyter

This tutorial uses the [`tabulario/spark-iceberg`](https://hub.docker.com/r/tabulario/spark-iceberg) image, which bundles Spark, Jupyter, and the Iceberg runtime. The `notebooks` directory contains a `tableflow-quickstart.ipynb` notebook, which is mounted into the container and appears in Jupyter.

The notebook prompts for your Tableflow credentials, so the container doesn't need them for this tutorial. If you're continuing to Part 3, where a dashboard queries this table through the container's Spark Thrift server, also export the values from Step 1 before starting the container in the next step. [`tableflow-entrypoint.sh`](tableflow-entrypoint.sh) uses them to set the Tableflow REST Catalog as the Thrift server's default catalog:

```shell
export TABLEFLOW_REST_URI=<REST_CATALOG_ENDPOINT>
export TABLEFLOW_API_KEY=<TABLEFLOW_API_KEY>
export TABLEFLOW_API_SECRET=<TABLEFLOW_API_SECRET>
```

Set `CLUSTER_REGION` to your Confluent Cloud cluster's region, then start the container. The following commands use `us-east-1`. Substitute your region, which is the `Region` value in the output of `confluent kafka cluster describe`:

```shell
export CLUSTER_REGION=us-east-1
docker compose up -d
```

If the container is already running, recreate it with `docker compose up -d --force-recreate` after exporting the variables.

Confirm the container is running:

```shell
docker ps
```

## Step 3: Query the Iceberg table

Open [http://localhost:8888](http://localhost:8888) in your browser, then open `notebooks/tableflow-quickstart.ipynb`.

Run the first code cell. It prompts for the values from Step 1:

* **Tableflow REST Catalog endpoint**: your REST Catalog endpoint
* **Tableflow API key**: your Tableflow API key
* **Tableflow API secret**: your Tableflow API secret (input is hidden)

The notebook reads the secret with Python's `getpass`, so it isn't stored in the notebook source, which Jupyter auto-saves. Don't paste it into a cell.

In the remaining cells, replace `<your-kafka-cluster-id>` with your Kafka cluster ID, the `lkc-...` value from `confluent kafka cluster describe` (not the cluster name).

Run each cell in order from the **Run** menu. The `SHOW TABLES` cell lists the tables Tableflow has published for your cluster. The `SELECT *` cell returns the stock trades that the Datagen connector from Part 1 is producing. The table also has a `key` column and Kafka metadata columns with names that start with `$$` (such as `$$offset` and `$$timestamp`), which this sample omits:

```text
+------+--------+-------+-----+--------+-------+
| side |quantity| symbol|price|account | userid|
+------+--------+-------+-----+--------+-------+
| SELL |    1120|  ZWZZT|   95| ABC123 |User_9 |
|  BUY |     540|  ZJZZT|   61| XYZ789 |User_4 |
+------+--------+-------+-----+--------+-------+
```

> **Note**: If `SHOW TABLES` lists `stock-trades` but `SELECT *` returns no rows, Tableflow is still writing the table's first data files. This can take a few minutes after the topic reaches `RUNNING`. Wait and run the cell again.

Wait a minute and re-run the `SELECT *` cell. New rows appear because the Datagen connector is still producing and Tableflow is still syncing.

> **Note**: If you get `org.apache.iceberg.exceptions.ForbiddenException: Forbidden: not authorized to sign the request`, verify that `CLUSTER_REGION` matches your Kafka cluster's region.

Querying Iceberg with Spark covers the historical side. In [Part 3](https://developer.confluent.io/confluent-tutorials/analytics-lightning-queries/), you'll build a dashboard that combines live data (via lightning queries) with historical data (via Spark on this Iceberg table), both derived from the same stream of trades.

## Clean up

Continue to [Part 3](https://developer.confluent.io/confluent-tutorials/analytics-lightning-queries/), where you'll query real-time data with lightning queries. Leave the Confluent Cloud resources running. Part 3 uses the same environment and cluster, and the Datagen connector needs to keep producing trades.

Stop the Spark container:

```shell
docker compose down
```

Part 3's dashboard has an optional historical tab that queries the Iceberg table through this container. To use it, restart the container with the `docker compose up -d` command from Step 2.

When you're finished with the whole series, delete the Confluent Cloud environment created for this tutorial. First get the environment ID of the form `env-123456`:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

The Tableflow API key you created in Step 1 isn't tied to an environment, so deleting the environment doesn't delete it. Delete it separately. List your API keys:

```shell
confluent api-key list --current-user -o json
```

Then delete the key whose resource is `tableflow`:

```shell
confluent api-key delete <TABLEFLOW_API_KEY>
```
