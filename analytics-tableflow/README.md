<!-- title: Streamhouse for Analytics Part 1 of 3: Land a Kafka topic in Iceberg with Tableflow -->
<!-- description: In this tutorial, learn how to materialize a Kafka topic as an Iceberg table with Tableflow, with step-by-step instructions and supporting code. -->

# Streamhouse for Analytics Part 1 of 3: Land a Kafka topic in Iceberg with Tableflow

[Tableflow](https://docs.confluent.io/cloud/current/topics/tableflow/overview.html) is a Confluent Cloud feature that continuously materializes a schematized Kafka topic as an [Apache Iceberg™](https://iceberg.apache.org/) or Delta Lake table. Instead of building a CDC pipeline or a scheduled export job to get topic data into a queryable table, you enable Tableflow on the topic, and Confluent Cloud keeps the table in sync as new records arrive.

Analytics workloads usually fall into two categories:

* *Historical* analytics asks questions over large volumes of accumulated data, such as trends, totals over months of trades, and ad hoc exploration. A data lake or warehouse fits well because storage is cheap and query engines can scan a lot of data.
* *Real-time* analytics asks about what's happening right now, such as the latest price and volume for a ticker. It needs fresh results with low latency and high concurrency, which batch pipelines and warehouses struggle to provide.

Applications built on streaming data often need both. This series shows how to build each from the same stream of events.

In this tutorial, you'll produce sample stock trade data to a topic and enable Tableflow on it, so every record that lands in Kafka also lands in an Iceberg table. This sets up the historical side. In [Part 2](https://developer.confluent.io/confluent-tutorials/analytics-query-iceberg/) of this series, you'll query that table with Spark. In [Part 3](https://developer.confluent.io/confluent-tutorials/analytics-lightning-queries/), you'll add the real-time side: Flink computes live statistics over the trades, and lightning queries serve them with low latency to a dashboard.

> **How this series relates to [Streamhouse](https://streamhouse.com/):** Streamhouse is an open, vendor-neutral data architecture that connects streaming and table-based systems so data stays current wherever it's consumed, instead of drifting out of sync between one-off pipelines. The Kafka topic you produce to here is operational data. The Iceberg table that Tableflow keeps in sync with it is the same data in your analytical estate, with no batch job in between.
>
> Over the three parts of this series, you'll build out the rest of that architecture:
>
> * **Part 1:** Land trades in Iceberg with Tableflow, where historical data resides.
> * **Part 2:** Query that table with Spark. Other Iceberg-compatible engines can query it too.
> * **Part 3:** Serve fresh results with low latency. Flink computes live statistics as trades arrive, and lightning queries serve them. A dashboard shows live and historical views over the same trade data.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* [jq](https://jqlang.org/) for parsing command line JSON output
* Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

Tableflow is a Confluent Cloud feature, so unlike most tutorials in this repository, this one has no Docker-only alternative.

## Create Confluent Cloud resources

Install the `confluent-quickstart` CLI plugin, which streamlines the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin to create the environment and cluster needed for this tutorial. You can specify a different cloud provider (`gcp` or `azure`) or region, but Part 3 requires AWS. To find the supported regions for a cloud provider, run `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name analytics-tutorial-env \
  --kafka-cluster-name analytics-tutorial-cluster
```

The plugin creates the environment and cluster and sets both as active in your CLI context.

## Create a topic

Create the `stock-trades` topic that Tableflow will materialize as an Iceberg table:

```shell
confluent kafka topic create stock-trades
```

## Produce sample data with the Datagen Source connector

Use the fully managed Datagen Source connector's built-in `STOCK_TRADES` quickstart schema to continuously produce sample trade data.

Create an API key for the connector to use:

```shell
confluent api-key create --resource $(confluent kafka cluster describe -o json | jq -r .id)
```

In `analytics-tableflow/datagen-stock-trades-connector.json`, replace `YOUR_API_KEY` with the API key and `YOUR_API_SECRET` with the API secret.

Provision the connector:

```shell
confluent connect cluster create --config-file ./analytics-tableflow/datagen-stock-trades-connector.json
```

When the connector is running (it can take a minute to start), verify that the `stock-trades` topic is populating. First, set the active API key to the one you just created:

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

Leave the connector running so Tableflow has records to materialize.

## Enable Tableflow on the topic

Get your Kafka cluster ID:

```shell
confluent kafka cluster describe
```

Enable Tableflow on the `stock-trades` topic using Confluent-managed storage. Confluent provisions and manages the object storage, so there's no bucket to configure:

```shell
confluent tableflow topic enable stock-trades --cluster <CLUSTER_ID>
```

`MANAGED` storage and `ICEBERG` table format are the defaults, so no additional flags are required. To write to your own S3, GCS, or ADLS bucket, see [Bring Your Own Storage](https://docs.confluent.io/cloud/current/topics/tableflow/how-to-guides/configure-storage/overview.html) in the Tableflow documentation.

## Verify that Tableflow is syncing

Materializing a new topic as an Iceberg table can take several minutes. Check progress with:

```shell
confluent tableflow topic describe stock-trades --cluster <CLUSTER_ID>
```

Wait for the `Phase` to change from `PENDING` to `RUNNING`. From then on, every record produced to `stock-trades` is written to an Iceberg table.

You now have a live Iceberg table, and you didn't write any code to create it. In [Part 2](https://developer.confluent.io/confluent-tutorials/analytics-query-iceberg/) of this series, you'll query that table with Spark.

## Clean up

If you're moving on to [Part 2](https://developer.confluent.io/confluent-tutorials/analytics-query-iceberg/) right away, leave these resources running. Tableflow needs to keep syncing.

When you're finished with the whole series, delete the Confluent Cloud environment created for this tutorial. First get the environment ID of the form `env-123456`:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

The Tableflow API key you create in Part 2 isn't tied to an environment, so deleting the environment doesn't delete it. Delete it separately. List your API keys:

```shell
confluent api-key list --current-user -o json
```

Then delete the key whose resource is `tableflow`:

```shell
confluent api-key delete <TABLEFLOW_API_KEY>
```
