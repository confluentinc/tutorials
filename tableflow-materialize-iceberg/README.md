<!-- title: How to materialize a Kafka topic as an Iceberg table with Tableflow -->
<!-- description: In this tutorial, learn how to materialize a Kafka topic as an Iceberg table with Tableflow, with step-by-step instructions and supporting code. -->

# Tableflow Part 1 of 2: How to materialize a Kafka topic as an Iceberg table with Tableflow

[Tableflow](https://docs.confluent.io/cloud/current/topics/tableflow/overview.html) is a Confluent Cloud feature that continuously materializes a schematized Kafka topic as an [Apache Iceberg™](https://iceberg.apache.org/) or Delta Lake table. Instead of standing up a CDC pipeline or a scheduled export job to get topic data into a table that analytics engines can query, you enable Tableflow on the topic directly, and Confluent Cloud keeps the table in sync with the topic as new records arrive.

In this tutorial, you'll produce sample stock trade data to a topic and enable Tableflow on it, so that every record landing in Kafka also lands in an Iceberg table, no separate pipeline required. In [Part 2](https://developer.confluent.io/confluent-tutorials/tableflow-query-iceberg-spark/) of this series, you'll query that table with Spark.

> **A note on how this tutorial relates to a broader [Streamhouse](https://streamhouse.com/) data architecture:** Streamhouse is an openly defined vendor-neutral data architecture for connecting streaming and table-based systems so that data stays continuously current wherever it's consumed, instead of drifting out of sync between one-off pipelines. The Kafka topic you produce to in this tutorial is operational data; the Iceberg table Tableflow keeps in sync with it is that same data landing in your analytical estate, with no batch job in between. That's the pattern Streamhouse describes in miniature.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* [jq](https://jqlang.org/) for parsing command line JSON output
* Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

Tableflow is a Confluent Cloud feature, so unlike most tutorials in this repository, there is no Docker-only alternative here. You'll need a Confluent Cloud account to complete this tutorial.

## Create Confluent Cloud resources

Install the `confluent-quickstart` CLI plugin, which streamlines the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin to create the environment and cluster needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions for a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name tableflow-tutorial-env \
  --kafka-cluster-name tableflow-tutorial-cluster
```

The plugin logs you in, creates the environment and cluster, and sets both as active in your CLI context.

## Create a topic

Create the `stock-trades` topic that Tableflow will materialize as an Iceberg table:

```shell
confluent kafka topic create stock-trades
```

## Produce sample data with the Datagen Source connector

Rather than hand-typing records, use the fully-managed Datagen Source connector's built-in `STOCK_TRADES` quickstart schema to continuously produce sample trade data.

Create an API key for the connector to use:

```shell
confluent api-key create --resource $(confluent kafka cluster describe -o json | jq -r .id)
```

Substitute the API key and secret for `YOUR_API_KEY` and `YOUR_API_SECRET`, respectively, in `datagen-stock-trades-connector.json`.

Provision the connector:

```shell
confluent connect cluster create --config-file ./tableflow-materialize-iceberg/datagen-stock-trades-connector.json
```

Once the connector is running, verify that the `stock-trades` topic is populating. You may need to wait a minute for the connector to start. First set the active API key using the key you just created:

```shell
confluent api-key use <YOUR_API_KEY>
```

Consume from the topic:

```shell
confluent kafka topic consume stock-trades --from-beginning --value-format avro
```

You should see a steady stream of trade records like:

```json
{"side":"SELL","quantity":1120,"symbol":"ZWZZT","price":95,"account":"ABC123","userid":"User_9"}
```

Leave the connector running. Tableflow needs an ongoing stream of records to materialize.

## Enable Tableflow on the topic

Get your Kafka cluster ID:

```shell
confluent kafka cluster describe
```

Enable Tableflow on the `stock-trades` topic using Confluent-managed storage, which is the simplest option since Confluent provisions and manages the underlying object storage for you (no bucket to configure):

```shell
confluent tableflow topic enable stock-trades --cluster <CLUSTER_ID>
```

`MANAGED` storage and `ICEBERG` table format are both defaults for this command, so no additional flags are required. If you'd rather write to your own S3, GCS, or ADLS bucket, see [Bring Your Own Storage](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/configure-storage/overview.html) in the Tableflow documentation.

## Verify that Tableflow is syncing

Materializing a newly created topic as an Iceberg table can take several minutes. Check on progress with:

```shell
confluent tableflow topic describe stock-trades --cluster <CLUSTER_ID>
```

You're looking for the topic's status to move from `PENDING` to `RUNNING`. Once it does, every record produced to `stock-trades` is being written into an Iceberg table, with no separate job in between.

At this point you have a live Iceberg table with no code of your own materializing it. In [Part 2](https://developer.confluent.io/confluent-tutorials/tableflow-query-iceberg-spark/) of this series, you'll query that table with Spark.

## Clean up

If you're moving on to [Part 2](https://developer.confluent.io/confluent-tutorials/tableflow-query-iceberg-spark/) right away, leave these resources running. Tableflow needs to keep syncing.

When you're finished with the whole series, delete the Confluent Cloud environment created for this tutorial. First get the environment ID of the form `env-123456`:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
