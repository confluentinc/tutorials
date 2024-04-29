<!-- title: How to compute the sum of a field with ksqlDB -->
<!-- description: In this tutorial, learn how to compute the sum of a field with ksqlDB, with step-by-step instructions and supporting code. -->

# How to compute the sum of a field with ksqlDB

Suppose you have a topic with events that represent ticket sales for movies. Each event contains the movie that the ticket was purchased for, as well as its price. In this tutorial, we'll write a program that calculates the sum of all ticket sales per movie.

## Setup

First we need to create a stream of ticket sales. This line of ksqlDB DDL creates a stream and its underlying Kafka topic `movie-ticket-sales` to represent
the ticket sale event. If the topic already exists, then ksqlDB simply registers it as the source of data underlying the new stream.
The stream has three fields: `title`, the name of the movie; `sale_ts`, the timestamp of the purchase; and
`ticket_total_value`, the amount paid for the ticket.  Another important characteristic of the data is the timestamp column, `sale_ts`. Every message in Kafka is timestamped, and unless you specify otherwise, ksqlDB will use that existing timestamp for any time-related processing. 
In this tutorial, we’re telling it to use a field in the message for the timestamp. 

```sql
 CREATE STREAM MOVIE_TICKET_SALES (title VARCHAR, sale_ts VARCHAR, ticket_total_value INT)
    WITH (KAFKA_TOPIC='movie-ticket-sales',
          PARTITIONS=1,
          VALUE_FORMAT='avro',
          TIMESTAMP='sale_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX');
```

Before we get too far, let’s set the `auto.offset.reset` configuration parameter to earliest. This means all new ksqlDB queries will
automatically compute their results from the beginning of a stream, rather than the end. This isn’t always what you’ll want to do in
production, but it makes query results much easier to see in examples like this.

`SET 'auto.offset.reset' = 'earliest';`
For the purposes of this tutorial only, we are also going to configure ksqlDB to
buffer the aggregates as it builds them. This makes the query feel like it responds more slowly,
but means that you get just one row per movie from which it is simpler to understand the concept:

`SET 'ksql.streams.cache.max.bytes.buffering' = '10000000';`

## Computing the sum

Now that you have your stream the next step is to create a table for performing the sum of sales per movie title.

```sql
CREATE TABLE MOVIE_REVENUE AS
SELECT TITLE,
       SUM(TICKET_TOTAL_VALUE) AS TOTAL_VALUE
FROM MOVIE_TICKET_SALES
GROUP BY TITLE;
```
