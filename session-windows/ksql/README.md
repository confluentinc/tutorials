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
       FORMAT_TIMESTAMP(FROM_UNIXTIME(WINDOWEND),'yyyy-MM-dd HH:mm:ss', 'UTC')   AS session_end_ts,
       COUNT(*) AS click_count,
       WINDOWEND - WINDOWSTART AS session_length_ms
FROM CLICKS
WINDOW SESSION (5 MINUTES)
GROUP BY ip
EMIT CHANGES;
```
