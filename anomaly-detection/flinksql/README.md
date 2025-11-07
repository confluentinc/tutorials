<!-- title: How to detect anomalies in a Kafka topic with Flink SQL -->
<!-- description: In this tutorial, learn how to detect anomalies in a Kafka topic with Flink SQL, with step-by-step instructions and supporting code. -->

# How to detect anomalies in a Kafka topic with Flink SQL

This tutorial demonstrates how to detect anomalies in a Kafka topic using Confluent Cloud for Apache Flink®. You will set up the necessary resources in Confluent Cloud and run built-in ARIMA model-based anomaly detection queries against example order data.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine

## Create Confluent Cloud resources

Login to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that streamlines resource creation in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`. The plugin should complete in under a minute.

```shell
confluent quickstart \
  --environment-name anomaly-detection-env \
  --kafka-cluster-name anomaly-detection-cluster \
  --compute-pool-name anomaly-detection-pool
```

## Open Flink shell

Start a Flink SQL shell:

```shell
confluent flink shell --compute-pool \
  $(confluent flink compute-pool list -o json | jq -r ".[0].id")
```

## Run anomaly detection queries

Next, you’ll run a series of anomaly detection queries against [mock data streams](https://docs.confluent.io/cloud/current/flink/reference/example-data.html) in Confluent Cloud, specifically the [`orders` stream](https://docs.confluent.io/cloud/current/flink/reference/example-data.html#orders-table).

Confluent Cloud for Apache Flink® provides a built-in anomaly detection function, [`ML_DETECT_ANOMALIES`](https://docs.confluent.io/cloud/current/flink/reference/functions/model-inference-functions.html#flink-sql-ml-anomaly-detect-function), which identifies outliers in a data stream based on an [ARIMA model](https://docs.confluent.io/cloud/current/ai/builtin-functions/detect-anomalies.html#arima-model).

Anomaly detection operates over a defined window. For example, to find global outliers, specify an `OVER` window that includes previous rows:

```sql
OVER (ORDER BY $rowtime
      RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
```

The following query extracts the Boolean value that indicates whether a record is an anomaly, ignoring rows for which no prediction is made (because there isn’t enough data yet). Here, we set two [model hyperparameters](https://docs.confluent.io/cloud/current/ai/builtin-functions/detect-anomalies.html#anomaly-detection-parameters):
  - horizon = 1 (forecast one time period ahead)
  - confidencePercentage = 90.0

```sql
SELECT
  customer_id,
  ts,
  price,
  anomaly_results[6] AS is_anomaly
FROM (
  SELECT
    customer_id,
    $rowtime as ts,
    price,
    ML_DETECT_ANOMALIES(price, $rowtime, JSON_OBJECT('horizon' VALUE 1, 'confidencePercentage' VALUE 90.0))
      OVER (ORDER BY $rowtime
            RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS anomaly_results
  FROM `examples`.`marketplace`.`orders`)
WHERE anomaly_results[6] IS NOT NULL;
```

You’ll notice that some very large or small orders are flagged as anomalies, for example:

```plaintext
customer_id ts                       price  is_anomaly
3012        2025-11-03 10:19:34.558  17.37  FALSE
3180        2025-11-03 10:19:34.598  10.43  TRUE
3218        2025-11-03 10:19:34.558  27.97  FALSE
3161        2025-11-03 10:19:34.559  63.72  FALSE
3171        2025-11-03 10:19:34.578  69.95  FALSE
3163        2025-11-03 10:19:34.598  79.18  FALSE
3063        2025-11-03 10:19:34.618  40.93  FALSE
3058        2025-11-03 10:19:34.638  99.69  TRUE
```

Because anomaly detection works as an `OVER` aggregation query, you can define exogenous variables that establish different cohorts with unique definitions of "outlier." For example, a large order from a platinum member who frequently places large orders may be typical, whereas a large order from a new anonymous customer may be anomalous.

The following query is similar to the previous one, but partitions the window by `customer_id` and lowers the `minTrainingSize` hyperparameter to 16 to get results sooner. This effectively defines anomalous behavior per customer:

```sql
SELECT
  customer_id,
  ts,
  price,
  anomaly_results[6] AS is_anomaly
FROM (
  SELECT
    customer_id,
    $rowtime as ts,
    price,
    ML_DETECT_ANOMALIES(price, $rowtime, JSON_OBJECT('horizon' VALUE 1, 'minTrainingSize' VALUE 16, 'confidencePercentage' VALUE 90.0))
      OVER (PARTITION BY customer_id
            ORDER BY $rowtime
            RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS anomaly_results
  FROM `examples`.`marketplace`.`orders`)
WHERE anomaly_results[6] IS NOT NULL;
```

If you let the query run long enough, you’ll observe that some customers who usually place small orders will have large outlier orders. Others who typically place large orders will have small outlier orders. For example:

```plaintext
customer_id ts                       price  is_anomaly
...
3020        2025-11-03 11:15:46.524  43.48  FALSE
3020        2025-11-03 11:16:10.421  30.02  FALSE
3020        2025-11-03 11:15:08.424  21.39  FALSE
3020        2025-11-03 11:16:10.918  97.86  TRUE
...
3183        2025-11-03 11:10:08.829  70.91  FALSE
3183        2025-11-03 11:12:11.514  10.53  TRUE
3183        2025-11-03 11:13:21.092  91.22  FALSE
3183        2025-11-03 11:16:09.783  87.10  FALSE
...
```

## Clean up

When you are finished, delete the `anomaly-detection-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```
