<!-- title: How to count the number of events in a Kafka topic with ksqlDB -->
<!-- description: In this tutorial, learn how to count the number of events in a Kafka topic with ksqlDB, with step-by-step instructions and supporting code. -->

#  How to count the number of events in a Kafka topic with ksqlDB

This tutorial takes a stream of individual movie ticket sales events and counts the total number of tickets sold per movie. Not all ticket prices are the same (apparently some of these theaters are fancier than others), but the task of the ksqlDB query is just to group and count regardless of ticket price.

## Setup

First we need to create a stream of ticket sales. This line of ksqlDB DDL creates a stream and its underlying Kafka topic to represent 
the annual sales totals. If the topic already exists, then ksqlDB simply registers it as the source of data underlying the new stream. 
The stream has three fields: `title`, the name of the movie; `sale_ts`, the time at which the ticket was sold; and 
`ticket_total_value`, the price paid for the ticket. The statement also specifies the underlying Kafka topic as `movie-ticket-sales`, 
that it should have a single partition, and defines Avro as its data format.

```sql
CREATE STREAM MOVIE_TICKET_SALES (title VARCHAR, sale_ts VARCHAR, ticket_total_value INT)
    WITH (KAFKA_TOPIC='movie-ticket-sales',
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

## Computing the count

Now that you have your stream the next step is to create a table for performing a count of the number of movie tickets.

```sql
CREATE TABLE MOVIE_TICKETS_SOLD AS 
    SELECT TITLE, 
           COUNT(TICKET_TOTAL_VALUE) AS TICKETS_SOLD
    FROM MOVIE_TICKET_SALES
    GROUP BY TITLE
    EMIT CHANGES;
```
