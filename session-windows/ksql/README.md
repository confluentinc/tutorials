<!-- title: How to aggregate over session windows with ksqlDB -->
<!-- description: In this tutorial, learn how to aggregate over session windows with ksqlDB, with step-by-step instructions and supporting code. -->

# How to aggregate over session windows with ksqlDB

If you have time series events in a Kafka topic, session windows let you group and aggregate them into variable-size, non-overlapping time intervals based on a configurable inactivity period.

For example, you have a topic with events that represent website clicks. In this tutorial we'll write a query to count the number of clicks per key source IP address for windows that close after 5 minutes of inactivity.

## Setup

First we need to create a stream of clicks. This line of ksqlDB DDL creates a stream and its underlying Kafka topic to represent 
a stream of website clicks:

```sql
CREATE STREAM clicks (ip VARCHAR, url VARCHAR, timestamp VARCHAR)
    WITH (KAFKA_TOPIC='clicks',
          TIMESTAMP='timestamp',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

## Compute aggregation over session windows

Given the stream of clicks, compute the count of clicks per session window, where a window closes after 5 minutes of inactivity.

```sql
SELECT ip,
       FORMAT_TIMESTAMP(FROM_UNIXTIME(WINDOWSTART),'yyyy-MM-dd HH:mm:ss', 'UTC') AS session_start_ts,
       FORMAT_TIMESTAMP(FROM_UNIXTIME(WINDOWEND),'yyyy-MM-dd HH:mm:ss', 'UTC') AS session_end_ts,
       COUNT(*) AS click_count,
       WINDOWEND - WINDOWSTART AS session_length_ms
FROM clicks
WINDOW SESSION (5 MINUTES)
GROUP BY ip
EMIT CHANGES;
```

## Running the example

### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

### Run the commands

Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

Start ksqlDB and Kafka:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml up -d
```

Next, open the ksqlDB CLI:

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Run the following SQL statements to create the `clicks` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM clicks (ip VARCHAR, timestamp VARCHAR, url VARCHAR)
    WITH (KAFKA_TOPIC='clicks',
          TIMESTAMP='timestamp',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

```sql
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP()),'yyyy-MM-dd''T''HH:mm:ssX'),'/etiam/justo/etiam/pretium/iaculis.xml');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (60 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/nullam/orci/pede/venenatis.json');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (91 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/mauris/morbi/non.jpg');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (96 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/convallis/nunc/proin.jsp');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (2 * 60 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/vestibulum/vestibulum/ante/ipsum/primis/in.json');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (63 * 60 * 1000) + 21),'yyyy-MM-dd''T''HH:mm:ssX'),'/vehicula/consequat/morbi/a/ipsum/integer/a.jpg');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (63 * 60 * 1000) + 50),'yyyy-MM-dd''T''HH:mm:ssX'),'/pede/venenatis.jsp');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (100 * 60 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/nec/euismod/scelerisque/quam.xml');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (100 * 60 * 1000) + 9),'yyyy-MM-dd''T''HH:mm:ssX'),'/ligula/nec/sem/duis.jsp');
```

Next, run the session window query to compute the count of clicks per session window, where a window closes after 5 
minutes of inactivity. Note that we first tell ksqlDB to consume from the beginning of the stream, and we also
configure the query to use caching so that we only get one row per session window.

```sql
SET 'auto.offset.reset'='earliest';
SET 'ksql.streams.cache.max.bytes.buffering' = '10000000';

SELECT ip,
       FORMAT_TIMESTAMP(FROM_UNIXTIME(WINDOWSTART),'yyyy-MM-dd HH:mm:ss', 'UTC') AS session_start_ts,
       FORMAT_TIMESTAMP(FROM_UNIXTIME(WINDOWEND),'yyyy-MM-dd HH:mm:ss', 'UTC') AS session_end_ts,
       COUNT(*) AS click_count,
       WINDOWEND - WINDOWSTART AS session_length_ms
FROM clicks
WINDOW SESSION (5 MINUTES)
GROUP BY ip
EMIT CHANGES;
```

The query output should look like this:

```plaintext
+---------------------------+---------------------------+---------------------------+---------------------------+---------------------------+
|IP                         |SESSION_START_TS           |SESSION_END_TS             |CLICK_COUNT                |SESSION_LENGTH_MS          |
+---------------------------+---------------------------+---------------------------+---------------------------+---------------------------+
|51.56.119.117              |2024-09-27 14:31:02        |2024-09-27 14:32:38        |3                          |96000                      |
|53.170.33.192              |2024-09-27 14:32:33        |2024-09-27 14:33:03        |2                          |30000                      |
|51.56.119.117              |2024-09-27 15:34:03        |2024-09-27 15:34:03        |2                          |0                          |
|53.170.33.192              |2024-09-27 16:11:03        |2024-09-27 16:11:03        |2                          |0                          |
+---------------------------+---------------------------+---------------------------+---------------------------+---------------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
