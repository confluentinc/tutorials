<!-- title: How to enable real-time analytics with Lightning Queries -->
<!-- description: In this tutorial, learn how to compute live last-N-trades stock statistics with a Flink Process Table Function, serve them with the Confluent Cloud Real-Time Context Engine, and query them in real time with lightning queries, with step-by-step instructions and supporting code. -->

# Analytics Part 3 of 3: How to enable real-time analytics with Lightning Queries

In [Part 1](https://developer.confluent.io/confluent-tutorials/analytics-tableflow/) and [Part 2](https://developer.confluent.io/confluent-tutorials/analytics-query-iceberg/) of this series, you materialized a Kafka topic of stock trades as an Iceberg table and queried it with Spark. Spark is a good fit for exploratory analysis over history. When an application needs real-time, high-concurrency serving, Spark is often paired with a separate serving store. Lightning queries take a different approach: they serve results from a continuously updated copy of a Kafka topic, upstream of the data lake and closer to when events are generated and aggregated on the fly by a stream processing engine.

In this tutorial, you'll build a dashboard on that approach, with charts that refresh every few seconds. You'll:

1. Deploy a Flink [Process Table Function (PTF)](https://docs.confluent.io/cloud/current/flink/concepts/process-table-functions.html) that incrementally computes statistics per ticker over the last N trades (N is configurable)
2. Write the results to a compacted Kafka topic named `stats-by-ticker` with Flink SQL
3. Enable the [Real-Time Context Engine](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/overview.html) (RTCE) on that topic, which materializes it into a low-latency serving layer
4. Query it with a lightning query through the API
5. Run a local dashboard that issues lightning queries on a timer and charts the results, with an optional historical tab that queries the Iceberg table from Part 2

A [lightning query](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/overview.html) reads a durable, continuously updated copy of a Kafka topic, which Confluent refreshes within seconds of new data arriving. Like a snapshot query, it runs once and exits, but it targets low-latency, real-time responses and high concurrency. It supports point lookups, range scans, and full scans, which are the queries a dashboard issues.

The [Real-Time Context Engine](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/overview.html) provides MCP tools that issue lightning queries on behalf of AI agents (see the [Real-Time Context Engine tutorial](https://developer.confluent.io/confluent-tutorials/agentic-ai-real-time-context/flinksql/)). Here, you'll call the lightning query API directly.

> **Early Access.** The lightning query API is an Early Access Program feature in Confluent Cloud, intended for evaluation in development and testing environments only. See the [sign-up form](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/overview.html) to participate. RTCE is currently available on AWS only.

> **How this tutorial relates to [Streamhouse](https://streamhouse.com/):** Streamhouse describes five stages: capture, transport, transform, govern, and serve. Here, trades in Kafka are the transport stage. The PTF is the transform stage, updating statistics incrementally as each trade arrives instead of recomputing them in a batch job. The registered schema on `stats-by-ticker` is the govern stage, and lightning queries are the serve stage.

## Prerequisites

* Completion of [Part 1](https://developer.confluent.io/confluent-tutorials/analytics-tableflow/) of this tutorial series, with the Datagen connector left running so that `stock-trades` keeps receiving trades. Part 2 is required only for the dashboard's historical tab.
* A Kafka cluster in a region [supported by RTCE](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/limitations.html). If you used the default cloud provider in Part 1, you're on AWS.
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) and [jq](https://jqlang.org/)
* Java 17. Install it with the [OpenJDK instructions](https://openjdk.org/install/) if needed, and verify that `java -version` shows version 17.
* Python 3.9 or later, for the dashboard
* Optional, for the dashboard's historical tab: the Spark container from [Part 2](https://developer.confluent.io/confluent-tutorials/analytics-query-iceberg/), running with the `TABLEFLOW_REST_URI`, `TABLEFLOW_API_KEY`, and `TABLEFLOW_API_SECRET` environment variables set (see Part 2's Step 2)
* Navigate into the top-level directory of your `confluentinc/tutorials` clone (see Part 1 for cloning instructions)

## Inspect the PTF code

The [`LastNTradesStats`](last-n-trades-stats-ptf/src/main/java/io/confluent/developer/LastNTradesStats.java) class (located under `analytics-lightning-queries/last-n-trades-stats-ptf`) emits an updated result on every incoming trade instead of waiting for a window to close. Rather than using a time-based window, it keeps the most recent N trades per ticker, so each emission describes the last N trades regardless of how long ago they happened. Note the following:

* **Incremental maintenance.** State holds running totals for volume, price sum, buy count, and sell count, plus the last N trades, oldest first. A new trade adds to the totals. Once more than N trades are retained, the oldest trade is subtracted. The average is the price sum divided by the number of retained trades.
* **Min and max with minimal rescanning.** A new trade can only move the min or max in one direction, so a single comparison updates them. The retained trades are rescanned only when the evicted trade was the current min or max.
* **A configurable window size.** N is the function's `numTrades` argument, so you set it in SQL with no code change or redeployment.
* **No event time.** The window is count-based, so the PTF needs no `on_time` column, watermark, or timers. "Oldest" means the earliest trade to reach the PTF for that ticker. A ticker with fewer than N trades so far gets statistics over the trades it has.

```java
public void eval(
    Context ctx,
    @StateHint TradesState state,
    @ArgumentHint(name = "input", value = SET_SEMANTIC_TABLE) Row input,
    @ArgumentHint(name = "numTrades") Integer numTrades
) {
    // ... read the trade's quantity, price, and side ...

    addTrade(state, quantity.longValue(), price.doubleValue(), "SELL".equalsIgnoreCase(side));
    // Drop the oldest trades until only the last N remain
    boolean extremeEvicted = false;
    while (state.trades.size() > numTrades) {
        extremeEvicted |= evictOldest(state);
    }
    // Only rescan for min/max if an evicted trade could have been the min or the max
    if (extremeEvicted) {
        rescanMinMax(state);
    }
    emitStats(state);
}
```

State size is bounded by N per ticker. Removing the oldest trade from the front of a list is O(N), which is fine for up to a few thousand trades per ticker. For much larger N, consider a ring buffer.

## Set up a Flink compute pool

Check that the environment from Part 1 is active:

```shell
confluent environment list
```

If it isn't, set it as the active environment and select the cluster:

```shell
confluent environment use <ENVIRONMENT_ID>
confluent kafka cluster use <CLUSTER_ID>
```

Get the cluster's cloud provider, region, and ID, which you'll need in later steps:

```shell
confluent kafka cluster describe
```

Create a Flink compute pool in the same cloud and region. The commands in this tutorial use `aws` and `us-east-1`. Substitute your cluster's region if it's different:

```shell
confluent flink compute-pool create analytics-pool \
  --cloud aws \
  --region us-east-1 \
  --max-cfu 10
```

## Deploy and register the PTF

Build an uberjar containing all dependencies:

```shell
./gradlew analytics-lightning-queries:last-n-trades-stats-ptf:shadowJar
```

Upload the JAR as a Flink artifact:

```shell
confluent flink artifact create last_n_trades_stats_ptf \
    --artifact-file ./analytics-lightning-queries/last-n-trades-stats-ptf/build/libs/last-n-trades-stats-ptf-all.jar \
    --cloud aws \
    --region us-east-1
```

Note the returned artifact ID, which looks like `cfa-123456`. Open the Flink SQL shell:

```shell
confluent flink shell --compute-pool \
  $(confluent flink compute-pool list -o json | jq -r '.[] | select(.name == "analytics-pool") | .id')
```

Register the PTF as a function, replacing `cfa-123456` with your artifact ID:

```sql
CREATE FUNCTION LastNTradesStats
AS 'io.confluent.developer.LastNTradesStats'
USING JAR 'confluent-artifact://cfa-123456';
```

## Create the stats-by-ticker table

The Datagen connector registered a schema for `stock-trades`, so Flink already exposes it as a table. Inspect its columns:

```sql
DESCRIBE `stock-trades`;
```

The `STOCK_TRADES` quickstart schema has `side` (`BUY` or `SELL`), `quantity`, `symbol`, `price`, `account`, and `userid` columns.

Create the output table, which Flink backs with a topic named `stats-by-ticker`. The primary key, upsert changelog mode, and compacted cleanup policy make RTCE keep only the latest record for each ticker.

RTCE's [upsert requirements](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/limitations.html#upsert-requirements) also say that the Kafka record key must use raw byte format, and structured key schemas (Avro, Protobuf) aren't supported. When a table has a primary key, Flink defaults to the `avro-registry` key format, so set `'key.format' = 'raw'` explicitly. A raw key must be a single column named `key`, so the ticker symbol goes in a column with that name.

```sql
CREATE TABLE `stats-by-ticker` (
  `key` STRING,
  total_volume BIGINT,
  avg_price DOUBLE,
  min_price DOUBLE,
  max_price DOUBLE,
  num_sell_orders BIGINT,
  num_buy_orders BIGINT,
  PRIMARY KEY (`key`) NOT ENFORCED
) WITH (
  'changelog.mode' = 'upsert',
  'kafka.cleanup-policy' = 'compact',
  'key.format' = 'raw'
);
```

Start the continuous statement that populates it. It partitions trades by `symbol` and computes statistics over each ticker's last 100 trades. To use a different window, change `numTrades`:

```sql
INSERT INTO `stats-by-ticker`
SELECT
  symbol AS `key`,
  totalVolume AS total_volume,
  avgPrice AS avg_price,
  minPrice AS min_price,
  maxPrice AS max_price,
  numSellOrders AS num_sell_orders,
  numBuyOrders AS num_buy_orders
FROM LastNTradesStats(
  input => TABLE `stock-trades` PARTITION BY symbol,
  numTrades => 100
);
```

This statement runs until you stop it. Open a second Flink SQL shell (or exit this one, since the statement keeps running in Confluent Cloud) and confirm that statistics are arriving:

```sql
SELECT * FROM `stats-by-ticker`;
```

You should see one row per ticker, with values that change as trades arrive:

```plaintext
key     total_volume  avg_price  min_price  max_price  num_sell_orders  num_buy_orders
ZVZZT   104510        52.3       1.0        100.0      42               39
ZWZZT   98200         49.8       1.0        100.0      37               44
```

Because the table has a primary key and is in upsert mode, the topic keeps only the latest statistics per ticker, no matter how many updates the PTF emits.

## Enable RTCE on the stats-by-ticker topic

Exit the Flink SQL shell (`exit;`), then enable RTCE on the topic using your cluster's region:

```shell
confluent rtce rtce-topic create \
  --cloud aws \
  --region us-east-1 \
  --topic-name stats-by-ticker \
  --description "Live trading statistics per stock ticker over its last 100 trades: total_volume, avg_price, min_price, max_price, num_sell_orders, and num_buy_orders. One row per ticker. The key column holds the ticker symbol."
```

Check the phase of the RTCE topic:

```shell
confluent rtce rtce-topic describe stats-by-ticker
```

Re-run the command every few seconds until the `Phase` changes from `PENDING` (or `PROVISIONING`) to `ACTIVE`. Even then, the first queries can take a moment to succeed, as described in the next sections.

## Create credentials

The lightning query API uses HTTP Basic authentication with a global-scope Confluent Cloud API key, the same credential that RTCE's MCP endpoint uses. Create one:

```shell
confluent api-key create --resource global --description "Lightning query tutorial"
```

Save the API key and secret, then generate the Base64 token for the `Authorization` header and set the identifiers the API needs:

```shell
export TOKEN=$(printf '%s' '<API_KEY>:<API_SECRET>' | base64 | tr -d '\n')
export ENV_ID=$(confluent environment describe -o json | jq -r .id)
export CLUSTER_ID=$(confluent kafka cluster describe -o json | jq -r .id)
```

A new API key can take up to a minute to start working. If your first request returns `401 unauthenticated`, wait a few seconds and retry.

Your principal also needs the `DeveloperRead` role on the `stats-by-ticker` topic and on Schema Registry. If you created the environment and cluster yourself, you're an `OrganizationAdmin` and already have access.

## Issue a lightning query

Send a read-only SQL statement to the lightning query endpoint. Right after RTCE becomes `ACTIVE`, the table may still be loading, and requests can return errors like `Table data is not yet available`, `The Lightning Table is temporarily unavailable`, or `Failed to obtain data plane token from token platform`. These clear within a couple of minutes, so retry until you get rows back. First, list the tables that have RTCE enabled:

```shell
curl -s -X POST \
  https://sql.us-east-1.aws.confluent.cloud/query/v1alpha1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $TOKEN" \
  -d "{\"catalog_name\": \"$ENV_ID\", \"database_name\": \"$CLUSTER_ID\", \"query\": \"SHOW TABLES\"}" | jq
```

Now query the five tickers with the highest total volume. The topic name contains a hyphen, so double-quote it in the query:

```shell
curl -s -X POST \
  https://sql.us-east-1.aws.confluent.cloud/query/v1alpha1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $TOKEN" \
  -d "{
    \"catalog_name\": \"$ENV_ID\",
    \"database_name\": \"$CLUSTER_ID\",
    \"query\": \"SELECT key, total_volume FROM \\\"stats-by-ticker\\\" ORDER BY total_volume DESC LIMIT 5\"
  }" | jq
```

The response contains the schema and rows inline. With the default JSON result format, every value is string-encoded:

```json
{
  "api_version": "query/v1alpha1",
  "kind": "Statement",
  "result": {
    "result_format": "JSON",
    "schema": {
      "columns": [
        {"name": "KEY", "type": {"type": "VARCHAR", "nullable": false}},
        {"name": "TOTAL_VOLUME", "type": {"type": "BIGINT", "nullable": true}}
      ]
    },
    "data": [
      ["ZVZZT", "104510"],
      ["ZWZZT", "98200"]
    ]
  }
}
```

Run the same query a few seconds later and the numbers will have changed. Lightning queries support point lookups (`WHERE key = 'ZVZZT'`), filters, ordering, and row limits. They don't support aggregates, joins, or subqueries, and they return at most 200 rows. That's why the PTF computes the statistics ahead of time: the query only has to read them. Lightning queries share a rate limit with the RTCE MCP tools. If you exceed it, the API returns a `429`.

## Run the dashboard

The [`dashboard`](dashboard/app.py) directory contains a single-page web app built with [Streamlit](https://streamlit.io/), a Python framework for data apps. It fits this tutorial for three reasons: the whole app is one Python file, the API credentials stay on your machine instead of in browser JavaScript (call the lightning query endpoint from a backend, not a browser), and `st.fragment(run_every=...)` re-runs only the live chart section on a timer. The app uses [Plotly](https://plotly.com/python/) for the bar charts and the [`wordcloud`](https://github.com/amueller/word_cloud) package for the word clouds.

On each refresh, the app issues one lightning query per metric, ordered by that metric in descending order. For example:

```sql
SELECT key, total_volume FROM "stats-by-ticker" ORDER BY total_volume DESC LIMIT 10
```

The **Live** tab shows a chart for each of the six statistics. The **Historical** tab computes the same statistics over every trade in the `stock-trades` Iceberg table from Part 2, using Spark SQL through the container's Thrift server:

```sql
SELECT symbol AS ticker, SUM(quantity) AS total_volume, AVG(price) AS avg_price,
       MIN(price) AS min_price, MAX(price) AS max_price, ...
FROM `<CLUSTER_ID>`.`stock-trades`
GROUP BY symbol
```

The tabs complement each other. The live tab answers "what's happening right now" from the last 100 trades per ticker, with low latency. The historical tab answers questions over all history, in seconds, from the same data.

Create a virtual environment and install the dependencies:

```shell
cd analytics-lightning-queries/dashboard
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Set the connection details as environment variables. The app reads the API key and secret from the environment so they never land in source control:

```shell
export CONFLUENT_API_KEY=<API_KEY>
export CONFLUENT_API_SECRET=<API_SECRET>
export CONFLUENT_ENV_ID=$ENV_ID
export CONFLUENT_CLUSTER_ID=$CLUSTER_ID
export CONFLUENT_REGION=us-east-1
```

If the Spark container isn't running on `localhost:10000`, also set `SPARK_THRIFT_HOST` and `SPARK_THRIFT_PORT`. The historical tab needs the container from Part 2. The live tab works without it.

Start the app:

```shell
streamlit run app.py
```

Streamlit opens [http://localhost:8501](http://localhost:8501) in your browser. The sidebar lets you:

* Change the live tab's **refresh interval**, which defaults to 30 seconds. You can also set the default with the `REFRESH_SECONDS` environment variable.
* Change how many tickers appear in each chart
* Switch between **bar charts** and **word clouds**, where each ticker's size reflects the metric's value

Watch the live charts update as new trades flow through the PTF. On the historical tab, click **Re-run query** to recompute from the latest data (results are cached for 5 minutes), or expand **Run your own Spark SQL** to query the Iceberg table yourself.

## Further experimentation

* **Change the window.** Stop the `INSERT INTO` statement, change `numTrades => 100` to `numTrades => 10` (or 1000), and start it again. The PTF's state is tied to the statement, so the new statement builds its window from scratch.
* **Add a filter.** Use `WHERE key = 'ZVZZT'` to see a single-ticker point lookup, or `WHERE num_sell_orders > num_buy_orders` to find tickers under sell pressure.
* **Ask an agent.** An AI agent can now query the same topic through the RTCE MCP tools. See the [RTCE tutorial](https://developer.confluent.io/confluent-tutorials/agentic-ai-real-time-context/flinksql/) for how to connect one.

## Putting it together

In Part 2, the Spark notebook returned a handful of rows from `SELECT *`. That was enough to confirm that Tableflow was keeping the Iceberg table current, but it only sampled the table. The historical tab shows what Spark is for: aggregating over every trade ever written to the table. The dashboard's Spark query groups the full `stock-trades` table by ticker and computes total volume, average, minimum, and maximum price, and buy and sell counts. This query scans the entire table, which grows with each trade, and returns one row per ticker. The dashboard sends the query to the same Spark container from Part 2 over its Thrift server, and renders the result as charts alongside the live data.

Both tabs are derived from the same stream of trades, but they answer different questions and are served by different systems:

| | Live tab | Historical tab |
|---|---|---|
| **Question** | What's happening right now? | What has happened over all time? |
| **Scope** | Last 100 trades per ticker | Every trade in the Iceberg table |
| **Computed by** | Flink PTF, incrementally, as each trade arrives | Spark, on demand, by scanning the table |
| **Served from** | Lightning queries on the `stats-by-ticker` topic | Iceberg table maintained by Tableflow |
| **Latency** | Low latency, suitable for frequent refresh and many concurrent users | Seconds, so the dashboard caches results for 5 minutes |

Pairing the two avoids forcing one system to do both jobs. Spark is well suited to scanning large volumes of history but is a poor fit for serving many frequent queries. Lightning queries serve fresh, precomputed results quickly but don't hold the full history. Because both read from the same Kafka topic of trades, there's no export job or second database to keep in sync, and the live and historical views stay consistent with each other.

## Clean up

Stop the dashboard with `Ctrl+C`. Stop the Spark container from Part 2 by running the following from the top-level `tutorials` directory:

```shell
docker compose -f analytics-query-iceberg/docker-compose.yml down
```

When you're finished with the whole series, delete the Confluent Cloud environment created in Part 1. First get the environment ID of the form `env-123456`:

```shell
confluent environment list
```

Delete the environment, which also deletes the cluster, connector, Tableflow topic, Flink compute pool, RTCE topic, and everything else inside it:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

API keys aren't tied to an environment, so deleting the environment doesn't delete the ones you created for this series. List your API keys:

```shell
confluent api-key list --current-user -o json
```

Then delete the global API key from this tutorial (description: `Lightning query tutorial`) and the Tableflow API key from Part 2 (resource: `tableflow`):

```shell
confluent api-key delete <GLOBAL_API_KEY>
confluent api-key delete <TABLEFLOW_API_KEY>
```
