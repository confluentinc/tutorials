<!-- title: How to use a join in ksqlDB for anomaly detection -->
<!-- description: In this tutorial, learn how to use a join in ksqlDB for anomaly detection, with step-by-step instructions and supporting code. -->

# How to use a join in ksqlDB for anomaly detection

This tutorial gives examples of using a Stream-Table join to populate a table and then use windowing on the result table.  The use case for this tutorial 
is alerting of suspicious financial transactions over a 24-hour period.

## Setup

First, we'll need to create a stream of transactions:

```sql
CREATE STREAM transactions (TXN_ID BIGINT, USERNAME VARCHAR, RECIPIENT VARCHAR, AMOUNT DOUBLE, TS VARCHAR)
    WITH (kafka_topic='transactions',
          partitions=1,
          value_format='JSON',
          timestamp='TS',
          timestamp_format = 'yyyy-MM-dd HH:mm:ss');
```

Then create a table of known suspicious names:

```sql
CREATE TABLE suspicious_names (CREATED_TS VARCHAR,
                               COMPANY_NAME VARCHAR PRIMARY KEY,
                               COMPANY_ID INT)
    WITH (kafka_topic='suspicious_names',
          partitions=1,
          value_format='JSON',
          timestamp='CREATED_TS',
          timestamp_format = 'yyyy-MM-dd HH:mm:ss');
```

Now create the `TABLE` `suspicious_transactions` by joining the `transactions` `STREAM` with the `suspicious_accounts` `TABLE`

```sql
CREATE STREAM suspicious_transactions
    WITH (kafka_topic='suspicious_transactions', partitions=1, value_format='JSON') AS
    SELECT T.TXN_ID, T.USERNAME, T.RECIPIENT, T.AMOUNT, T.TS
    FROM transactions T
    INNER JOIN
    suspicious_names S
    ON T.RECIPIENT = S.COMPANY_NAME;
```

## Windowing the suspicious account activity
 
Then we'll wrap it all up in a table that captures activity with 3 or more suspicious transactions in a 24-hour period

```sql
CREATE TABLE accounts_to_monitor
    WITH (kafka_topic='accounts_to_monitor', partitions=1, value_format='JSON') AS
    SELECT TIMESTAMPTOSTRING(WINDOWSTART, 'yyyy-MM-dd HH:mm:ss Z') AS WINDOW_START, 
           TIMESTAMPTOSTRING(WINDOWEND, 'yyyy-MM-dd HH:mm:ss Z') AS WINDOW_END,
           USERNAME
    FROM suspicious_transactions
    WINDOW TUMBLING (SIZE 24 HOURS) 
    GROUP BY USERNAME
    HAVING COUNT(*) > 3;
```
The fields `WINDOW_START` and `WINDOW_END` tell us the time interval during which suspicious activity occurred. The `WINDOW TUMBLING` part of the query 
allows us to do an aggregation with distinct time boundaries. 
In this case, our window is fixed at a length of 24 hours, does not allow gaps, and does not allow overlapping.
