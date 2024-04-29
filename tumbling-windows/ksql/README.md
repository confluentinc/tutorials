<!-- title: How to aggregate over tumbling windows with ksqlDB -->
<!-- description: In this tutorial, learn how to aggregate over tumbling windows with ksqlDB, with step-by-step instructions and supporting code. -->

# How to aggregate over tumbling windows with ksqlDB

If you have time series events in a Kafka topic, tumbling windows let you group and aggregate them in fixed-size, non-overlapping, contiguous time intervals.

For example, in this tutorial we start with a stream of movie ratings and calculate the number of ratings per movie over 6-hour tumbling windows.

## Setup

First we need to create a stream of ticket sales. This line of ksqlDB DDL creates a stream and its underlying Kafka topic to represent 
a stream of movie ratings. If the topic already exists, then ksqlDB simply registers it as the source of data underlying the new stream. 
The stream has three fields: `title`, the name of the movie; `release_year`, the year the movie was released; `rating`, the rating a viewer gave it; and `timestamp`,
the time at which the rating was made.

```sql
CREATE STREAM ratings (title VARCHAR, release_year INT, rating DOUBLE, timestamp VARCHAR)
    WITH (kafka_topic='ratings',
          timestamp='timestamp',
          timestamp_format='yyyy-MM-dd HH:mm:ss',
          partitions=1,
          value_format='avro');
```

## Compute aggregation over tumbling windows

Given the stream of movie ratings, compute the count of ratings per title over 6-hour tumbling windows as follows:

```sql
SELECT title,
       COUNT(*) AS rating_count,
       WINDOWSTART AS window_start,
       WINDOWEND AS window_end
FROM ratings
WINDOW TUMBLING (SIZE 6 HOURS)
GROUP BY title
EMIT CHANGES;
```
