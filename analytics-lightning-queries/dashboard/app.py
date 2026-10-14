"""Stock stats dashboard with a live tab and a historical tab.

Live: every refresh, the app issues one lightning query per metric against the stats-by-ticker
topic, ordered by that metric descending, and renders the results as bar charts and word clouds.

Historical: the app queries the stock-trades Iceberg table (kept current by Tableflow) through the
Spark Thrift server running in the Part 2 container, aggregating over the full trade history.
"""
import base64
import os
from concurrent.futures import ThreadPoolExecutor

import pandas as pd
import plotly.express as px
import requests
import streamlit as st
from impala.dbapi import connect as spark_connect
from wordcloud import WordCloud

TOPIC = "stats-by-ticker"
METRICS = {
    "total_volume": "Total volume",
    "avg_price": "Average price",
    "min_price": "Min price",
    "max_price": "Max price",
    "num_sell_orders": "Sell orders",
    "num_buy_orders": "Buy orders",
}

HISTORY_TABLE = "stock-trades"
SPARK_HOST = os.environ.get("SPARK_THRIFT_HOST", "localhost")
SPARK_PORT = int(os.environ.get("SPARK_THRIFT_PORT", 10000))

st.set_page_config(page_title="Live stock stats", layout="wide")


def config(name):
    value = os.environ.get(name)
    if not value:
        st.error(f"Set the {name} environment variable and restart the app.")
        st.stop()
    return value


API_KEY = config("CONFLUENT_API_KEY")
API_SECRET = config("CONFLUENT_API_SECRET")
ENV_ID = config("CONFLUENT_ENV_ID")
CLUSTER_ID = config("CONFLUENT_CLUSTER_ID")
CLOUD = os.environ.get("CONFLUENT_CLOUD", "aws")
REGION = config("CONFLUENT_REGION")

ENDPOINT = f"https://sql.{REGION}.{CLOUD}.confluent.cloud/query/v1alpha1"
TOKEN = base64.b64encode(f"{API_KEY}:{API_SECRET}".encode()).decode()


def lightning_query(sql, max_rows):
    resp = requests.post(
        ENDPOINT,
        headers={"Authorization": f"Basic {TOKEN}", "Content-Type": "application/json"},
        json={
            "catalog_name": ENV_ID,
            "database_name": CLUSTER_ID,
            "query": sql,
            "options": {"max_result_rows": max_rows},
        },
        timeout=10,
    )
    if not resp.ok:
        raise RuntimeError(f"{resp.status_code}: {resp.text}")
    result = resp.json()["result"]
    # Column values arrive string-encoded under the JSON result format
    columns = [c["name"].lower() for c in result["schema"]["columns"]]
    return pd.DataFrame(result["data"], columns=columns)


def top_by_metric(metric, limit):
    # Topic name contains a hyphen, so it must be double-quoted
    sql = f'SELECT key, {metric} FROM "{TOPIC}" ORDER BY {metric} DESC LIMIT {limit}'
    df = lightning_query(sql, limit).rename(columns={"key": "ticker"})
    df[metric] = pd.to_numeric(df[metric])
    return df


def word_cloud(df, metric):
    freqs = {r.ticker: float(r[metric]) for _, r in df.iterrows() if float(r[metric]) > 0}
    if not freqs:
        return None
    wc = WordCloud(width=600, height=360, background_color="white", colormap="viridis")
    return wc.generate_from_frequencies(freqs).to_array()


def spark_query(sql):
    # The Part 2 container's Thrift server uses the Tableflow REST catalog as its default catalog,
    # so tables are addressed as `<cluster id>`.`<topic>`
    conn = spark_connect(host=SPARK_HOST, port=SPARK_PORT, user="spark", auth_mechanism="PLAIN")
    try:
        cur = conn.cursor()
        cur.execute(sql)
        columns = [c[0].lower() for c in cur.description]
        return pd.DataFrame(cur.fetchall(), columns=columns)
    finally:
        conn.close()


@st.cache_data(ttl=300, show_spinner="Querying the Iceberg table with Spark...")
def historical_stats():
    sql = f"""
        SELECT symbol AS ticker,
               SUM(quantity) AS total_volume,
               AVG(price) AS avg_price,
               MIN(price) AS min_price,
               MAX(price) AS max_price,
               SUM(CASE WHEN UPPER(side) = 'SELL' THEN 1 ELSE 0 END) AS num_sell_orders,
               SUM(CASE WHEN UPPER(side) = 'BUY' THEN 1 ELSE 0 END) AS num_buy_orders
        FROM `{CLUSTER_ID}`.`{HISTORY_TABLE}`
        GROUP BY symbol
    """
    df = spark_query(sql)
    for metric in METRICS:
        df[metric] = pd.to_numeric(df[metric])
    return df


def render_metric(df, metric, label):
    st.subheader(label)
    if df.empty:
        st.info("No data yet")
    elif view == "Bar charts":
        fig = px.bar(df, x="ticker", y=metric, labels={metric: label})
        fig.update_layout(height=320, margin=dict(l=0, r=0, t=10, b=0))
        st.plotly_chart(fig, use_container_width=True)
    else:
        img = word_cloud(df, metric)
        if img is not None:
            st.image(img, use_container_width=True)


st.title("Stock stats")
st.sidebar.header("Settings")
refresh_seconds = st.sidebar.number_input(
    "Live refresh interval (seconds)", min_value=1, value=int(os.environ.get("REFRESH_SECONDS", 30)), step=1
)
top_n = st.sidebar.number_input("Tickers per chart", min_value=1, max_value=200, value=10, step=1)
view = st.sidebar.radio("View", ["Bar charts", "Word clouds"])

live_tab, history_tab = st.tabs(["Live (lightning queries)", "Historical (Iceberg + Spark)"])


@st.fragment(run_every=refresh_seconds)
def live_dashboard():
    try:
        with ThreadPoolExecutor(max_workers=len(METRICS)) as pool:
            frames = dict(zip(METRICS, pool.map(lambda m: top_by_metric(m, top_n), METRICS)))
    except Exception as e:  # keep the dashboard alive and retry on the next refresh
        st.error(f"Lightning query failed: {e}")
        return

    st.caption(f"Updated {pd.Timestamp.now():%H:%M:%S}. Refreshing every {refresh_seconds}s.")
    cols = st.columns(3)
    for i, (metric, label) in enumerate(METRICS.items()):
        with cols[i % 3]:
            render_metric(frames[metric], metric, label)


def historical_dashboard():
    st.caption(
        f"Statistics over every trade in the `{HISTORY_TABLE}` Iceberg table, computed by Spark "
        f"({SPARK_HOST}:{SPARK_PORT}). Unlike the live tab, which covers each ticker's last 100 trades, "
        "this covers all of history. Results are cached for 5 minutes."
    )
    if st.button("Re-run query"):
        historical_stats.clear()
    try:
        stats = historical_stats()
    except Exception as e:
        st.error(f"Spark query failed: {e}")
        return

    cols = st.columns(3)
    for i, (metric, label) in enumerate(METRICS.items()):
        df = stats[["ticker", metric]].sort_values(metric, ascending=False).head(top_n)
        with cols[i % 3]:
            render_metric(df, metric, label)

    with st.expander("Run your own Spark SQL"):
        sql = st.text_area(
            "Query",
            value=f"SELECT * FROM `{CLUSTER_ID}`.`{HISTORY_TABLE}` LIMIT 20",
            height=100,
        )
        if st.button("Run"):
            try:
                st.dataframe(spark_query(sql), use_container_width=True)
            except Exception as e:
                st.error(f"Spark query failed: {e}")


with live_tab:
    live_dashboard()
with history_tab:
    historical_dashboard()
