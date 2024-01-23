
# Aggregations - min/max

This tutorial computes the highest grossing and lowest grossing films per year in a sample data set. To keep things simple, we’re going to create a source Kafka topic and ksqlDB stream with annual sales data in it.
## Setup

First we need to create a stream of ticket sales. This line of ksqlDB DDL creates a stream and its underlying Kafka topic to represent
the annual sales totals. If the topic already exists, then ksqlDB simply registers it as the source of data underlying the new stream.
The stream has three fields: `title`, the name of the movie; `release_year`, the year of the movie's release; and
`total_sales`, the total revenue for the movie. The statement also specifies the underlying Kafka topic as `movie-sales`,
that it should have a single partition, and defines Avro as its data format.

```sql
CREATE STREAM MOVIE_SALES (title VARCHAR, release_year INT, total_sales INT)
    WITH (KAFKA_TOPIC='movie-sales',
          PARTITIONS=1,
          VALUE_FORMAT='avro');
```

Before we get too far, let’s set the `auto.offset.reset` configuration parameter to earliest. This means all new ksqlDB queries will
automatically compute their results from the beginning of a stream, rather than the end. This isn’t always what you’ll want to do in
production, but it makes query results much easier to see in examples like this.

`SET 'auto.offset.reset' = 'earliest';`
For the purposes of this tutorial only, we are also going to configure ksqlDB to
buffer the aggregates as it builds them. This makes the query feel like it responds more slowly,
but means that you get just one row per movie from which it is simpler to understand the concept:

`SET 'ksql.streams.cache.max.bytes.buffering' = '10000000';`

## Computing the min/max

Now that you have your stream the next step is to create a table for calculating the minimum and maximum movie sales.

```sql
CREATE TABLE MOVIE_FIGURES_BY_YEAR AS
SELECT RELEASE_YEAR,
       MIN(TOTAL_SALES) AS MIN__TOTAL_SALES,
       MAX(TOTAL_SALES) AS MAX__TOTAL_SALES
FROM MOVIE_SALES
GROUP BY RELEASE_YEAR
    EMIT CHANGES;
```
