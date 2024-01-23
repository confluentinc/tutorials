# Column difference

This tutorial demonstrates how to calculate the difference between two columns.

## Setup

The first thing we do is to create a stream named `PURCHASE_STREAM`

```sql
CREATE STREAM PURCHASE_STREAM (
                  ID VARCHAR,
                  PREVIOUS_PURCHASE DOUBLE,
                  CURRENT_PURCHASE DOUBLE,
                  TXN_TS VARCHAR,
                  FIRST_NAME VARCHAR,
                  LAST_NAME  VARCHAR)

 WITH (KAFKA_TOPIC='customer_purchases',
       VALUE_FORMAT='JSON',
       PARTITIONS=1);
```

## Calculate the difference between two columns

Now create a query to determine the difference between two columns:

```sql
CREATE STREAM PURCHASE_HISTORY_STREAM AS
  SELECT FIRST_NAME,
         LAST_NAME,
         CURRENT_PURCHASE - PREVIOUS_PURCHASE as PURCHASE_DIFF
FROM PURCHASE_STREAM;
```
