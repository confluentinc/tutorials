<!-- title: How to query a Tableflow-synced Iceberg table with Spark -->
<!-- description: In this tutorial, learn how to query a Tableflow-synced Iceberg table with Spark, with step-by-step instructions and supporting code. -->

# Tableflow Part 2 of 2: How to query a Tableflow-synced Iceberg table with Spark

In [Part 1](https://developer.confluent.io/confluent-tutorials/tableflow-materialize-iceberg/) of this series, you enabled Tableflow on a Kafka topic, which continuously materializes it as an [Apache Iceberg™](https://iceberg.apache.org/) table. In this tutorial, you'll query that table directly from Spark, using Tableflow's built-in Iceberg REST Catalog. There is no Kafka topic export step, no second database, no sync job to maintain. The same governed data backing your Kafka topic is what Spark reads.

Tableflow's REST Catalog works the same way with any Iceberg-compatible query engine. This tutorial uses Spark because it runs entirely in a local Docker container, but the same catalog configuration pattern applies to other systems like [Snowflake](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-snowflake.html), [Trino](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-trino.html), [Amazon Athena](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-aws.html), and [DuckDB](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/query-engines/query-with-duckdb.html), if you'd rather try one of those.

> **A note on how this tutorial relates to a broader [Streamhouse](https://streamhouse.com/) data architecture:** Streamhouse is openly defined vendor-neutral data architecture for connecting streaming and table-based systems so that data stays continuously current wherever it's consumed, instead of drifting out of sync between one-off pipelines. Querying Tableflow's Iceberg table straight from Spark, with no export step or second database to keep in sync, is what that looks like from the analytics side: the same operational data flowing through your Kafka topic is what your analytical query engine reads, kept fresh automatically. That's the data flow Streamhouse describes in miniature.

## Prerequisites

* Completion of [Part 1](https://developer.confluent.io/confluent-tutorials/tableflow-materialize-iceberg/) of this tutorial series, with the Datagen connector and Tableflow left running
* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
* Navigate into the `tableflow-query-iceberg-spark` directory of your `confluentinc/tutorials` clone:
  ```shell
  cd tableflow-query-iceberg-spark
  ```

## Step 1: Set up access to the Iceberg REST Catalog

To query Tableflow's Iceberg tables, you need the REST Catalog endpoint URI and an API key scoped to Tableflow.

Get the REST Catalog endpoint:

1. In the [Confluent Cloud Console](https://confluent.cloud), navigate to your cluster and click **Tableflow** in the left navigation to open the Tableflow overview page.
2. In the **API access** section, copy the **REST Catalog Endpoint**. It resembles:
   ```
   https://tableflow.{CLOUD_REGION}.aws.confluent.cloud/iceberg/catalog/organizations/{ORG_ID}/environments/{ENV_ID}
   ```

Create an API key scoped to Tableflow:

```shell
confluent api-key create --resource tableflow
```

Save the key and secret. You'll enter them when prompted in the notebook in Step 3, so they never need to be written into the notebook itself.

## Step 2: Start Spark and Jupyter

This tutorial uses the [`tabulario/spark-iceberg`](https://hub.docker.com/r/tabulario/spark-iceberg) image, which bundles Spark, Jupyter, and the Iceberg runtime. The `notebooks` directory already contains a `tableflow-quickstart.ipynb` notebook, mounted into the container so it shows up in Jupyter automatically.

Set `CLUSTER_REGION` to your Confluent Cloud cluster's region (shown as `Region` in the output of `confluent kafka cluster describe` back in Part 1), then start the container:

```shell
export CLUSTER_REGION=<YOUR_CLUSTER_REGION>
docker compose up -d
```

Confirm the container is running:

```shell
docker ps
```

## Step 3: Query the Iceberg table

Open [http://localhost:8888](http://localhost:8888) in your browser and open the `tableflow-quickstart.ipynb` notebook under `notebooks`.

Run the first code cell. It prompts for the values from Step 1:

* **Tableflow REST Catalog endpoint**: your REST Catalog endpoint
* **Tableflow API key**: your Tableflow API key
* **Tableflow API secret**: your Tableflow API secret (input is hidden)

The secret is read with Python's `getpass`, so it is not stored in the notebook source, which Jupyter auto-saves. Don't paste it into a cell.

In the remaining cells, replace `<your-kafka-cluster-id>` with your Kafka cluster ID (the `lkc-...` value from `confluent kafka cluster describe`, not the cluster name).

Run each cell in order from the **Run** menu. The `SHOW TABLES` cell lists the tables Tableflow has published for your cluster, and the `SELECT *` cell returns the stock trade records the Datagen connector from Part 1 has been producing:

```
+------+--------+--------+-----+---------+
| side |quantity| symbol |price| account |
+------+--------+--------+-----+---------+
| SELL |    1120|   ZWZZT|   95|  ABC123 |
|  BUY |     540|   ZJZZT|   61|  XYZ789 |
+------+--------+--------+-----+---------+
```

Wait a minute and re-run the `SELECT *` cell. You'll see new rows, since the Datagen connector is still producing and Tableflow is still syncing, all without you touching a pipeline.

> **Note**: If you get `org.apache.iceberg.exceptions.ForbiddenException: Forbidden: not authorized to sign the request`, double check that `CLUSTER_REGION` matches your Kafka cluster's actual region.

## Clean up

Stop the Spark container:

```shell
docker compose down
```

When you're done, delete the Confluent Cloud environment you created for the tutorial. First get the environment ID of the form `env-123456`:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
