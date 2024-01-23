# Concatenation

In this tutorial, we'll show how to use the concatenation operator to create a single value from multiple columns.
       

## Setup

The first thing we do is create a stream named `ACTIVITY_STREAM` which simulates stock purchases and serves as our example of concatenating two columns together.

```sql
CREATE STREAM ACTIVITY_STREAM (
                  ID VARCHAR,
                  NUM_SHARES INT,
                  AMOUNT DOUBLE,
                  TXN_TS VARCHAR,
                  FIRST_NAME VARCHAR,
                  LAST_NAME  VARCHAR,
                  SYMBOL VARCHAR )

 WITH (KAFKA_TOPIC='stock_purchases',
       VALUE_FORMAT='JSON',
       PARTITIONS=1);
```
## Concatenating columns 

Now let's create a stream that concatenates several columns to create a summary of activity.

```sql
CREATE STREAM SUMMARY_RESULTS AS
  SELECT FIRST_NAME + ' ' + LAST_NAME +
       ' purchased ' +
       CAST(NUM_SHARES AS VARCHAR) +
       ' shares of ' +
       SYMBOL AS SUMMARY
FROM ACTIVITY_STREAM;
```
