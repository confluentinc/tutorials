<!-- title: How to forecast future values of time series data in a Kafka topic with Flink SQL -->
<!-- description: In this tutorial, learn how to forecast future values of time series data in a Kafka topic with Flink SQL, with step-by-step instructions and supporting code. -->

# How to forecast future values of time series data in a Kafka topic with Flink SQL

This tutorial demonstrates how to predict future values in time series data using Confluent Cloud for Apache Flink®. You will set up the necessary resources in Confluent Cloud and run numeric forecasting queries based on a built-in ARIMA model against example order data.

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
  --environment-name forecasting-env \
  --kafka-cluster-name forecasting-cluster \
  --compute-pool-name forecasting-pool
```

## Open Flink shell

Start a Flink SQL shell:

```shell
confluent flink shell --compute-pool \
  $(confluent flink compute-pool list -o json | jq -r ".[0].id")
```

## Run forecasting queries

Next, you’ll run a series of numeric forecasting queries against [mock data streams](https://docs.confluent.io/cloud/current/flink/reference/example-data.html) in Confluent Cloud, specifically the [`orders` stream](https://docs.confluent.io/cloud/current/flink/reference/example-data.html#orders-table).

Confluent Cloud for Apache Flink® provides a built-in forecasting function, [`ML_FORECAST`](https://docs.confluent.io/cloud/current/flink/reference/functions/model-inference-functions.html#flink-sql-ml-forecast-function), which predicts future values in a data stream based on an [ARIMA model](https://docs.confluent.io/cloud/current/ai/builtin-functions/forecast.html#arima-model).

Forecasting operates over a defined window. For example, to predict order price based on the historical trend of all orders, specify an `OVER` window that includes previous rows:

```sql
OVER (ORDER BY $rowtime
      RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
```

The following query extracts the forecasted order price, ignoring rows for which no prediction is made (because there isn’t enough data yet). Here, we set two [model hyperparameters](https://docs.confluent.io/cloud/current/ai/builtin-functions/forecast.html#forecasting-parameters):
  - horizon = 1 (forecast one time period ahead)
  - minTrainingSize = maxTrainingSize = 16 (train on short trends)

```sql
SELECT
  customer_id,
  ts,
  price,
  forecast[1][2] AS predicted_price
FROM (
  SELECT
    customer_id,
    $rowtime as ts,
    price,
    ML_FORECAST(price, $rowtime, JSON_OBJECT('horizon' VALUE 1, 'minTrainingSize' VALUE 16, 'maxTrainingSize' VALUE 16))
      OVER (ORDER BY $rowtime
            RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS forecast
  FROM `examples`.`marketplace`.`orders`)
WHERE forecast[1][2] IS NOT NULL;
```

Since the order prices in the example data stream are random, the forecast predictions will be a bit noisy. However, as short-term trends emerge, you’ll notice that directional changes in order prices “push” the predictions up or down in the expected direction.

```plaintext
customer_id ts                       price  predicted_price
...
3018        2025-11-04 10:11:30.011  54.09  68.41076134754304
3227        2025-11-04 10:11:30.011  95.88  78.81726651992819
3040        2025-11-04 10:11:30.111  93.51  104.90197028246105
3166        2025-11-04 10:11:30.111  98.77  106.81776297469208
...
```

Because forecasting works as an `OVER` aggregation query, you can define exogenous variables that may impact order size predictions, like the day of the week. This will let you determine if order prices trend differently depending on the day. 

The following query is similar to the previous one, but partitions the window by the day of week of the order timestamp. This effectively creates seven predictive models, one for each day of the week.

```sql
SELECT
  customer_id,
  ts,
  price,
  forecast[1][2] AS predicted_price
FROM (
  SELECT
    customer_id,
    $rowtime as ts,
    price,
    ML_FORECAST(price, $rowtime, JSON_OBJECT('horizon' VALUE 1, 'minTrainingSize' VALUE 16, 'maxTrainingSize' VALUE 16))
      OVER (PARTITION BY DAYOFWEEK($rowtime)
            ORDER BY $rowtime
            RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS forecast
  FROM `examples`.`marketplace`.`orders`)
WHERE forecast[1][2] IS NOT NULL;
```

## Clean up

When you are finished, delete the `forecasting-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```
