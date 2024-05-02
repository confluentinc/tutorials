<!-- title: How to filter duplicate events per-time window from a Kafka topic with ksqlDB -->
<!-- description: In this tutorial, learn how to filter duplicate events per-time window from a Kafka topic with ksqlDB, with step-by-step instructions and supporting code. -->

# How to filter duplicate events per-time window from a Kafka topic with ksqlDB

Consider a topic with events that represent clicks on a website. Each event contains an IP address, a URL, and a timestamp.
In this tutorial, we'll use ksqlDB to deduplicate these click events.

## Setup

Let's start with a stream representing a user click events:

```sql
CREATE STREAM CLICKS (IP_ADDRESS VARCHAR, URL VARCHAR)
    WITH (KAFKA_TOPIC = 'CLICKS',
          FORMAT = 'JSON',
          PARTITIONS=1);
```

Then create a table to collect the click events:

```sql
CREATE TABLE DETECTED_CLICKS AS
    SELECT
        IP_ADDRESS AS KEY1,
        URL AS KEY2,
        AS_VALUE(IP_ADDRESS) AS IP_ADDRESS,
        COUNT(IP_ADDRESS) as IP_COUNT,
        AS_VALUE(URL) AS URL,
        FORMAT_TIMESTAMP(FROM_UNIXTIME(EARLIEST_BY_OFFSET(ROWTIME)), 'yyyy-MM-dd HH:mm:ss.SSS') AS TIMESTAMP
    FROM CLICKS WINDOW TUMBLING (SIZE 2 MINUTES, RETENTION 1000 DAYS)
    GROUP BY IP_ADDRESS, URL;
```

As we’re grouping by ip-address and url, these columns will become part of the primary key of the table. Primary key columns are stored in the Kafka message’s key. As we’ll need them in the value later, we use `AS_VALUE` to copy the columns into the value and set their name. To avoid the value column names clashing with the key columns, we add aliases to rename the key columns.

As it stands, the key of the `DETECTED_CLICKS` table contains the ip-address, and url columns, and as the table is windowed, the window start and end timestamps.
Create another stream that will only contain `IP_ADDRESS`, `IP_COUNT`, `URL`, and `TIMESTAMP` from the topic backing the `DETECTED_CLICKS` table:
```sql
CREATE STREAM RAW_VALUES_CLICKS (IP_ADDRESS STRING, IP_COUNT BIGINT, URL STRING, TIMESTAMP STRING)
    WITH (KAFKA_TOPIC = 'DETECTED_CLICKS',
          PARTITIONS = 1,
          FORMAT = 'JSON');
```

Then create another stream that will de-duplicate the events.  The stream will set the key of the `DISTINCT_CLICKS` stream to just the IP address using the `PARTITION BY` statement. The `WHERE` clause is where we filter out duplicates by specifying to only retrieve IP addresses with a `IP_COUNT` of 1.

```sql
CREATE STREAM DISTINCT_CLICKS AS
    SELECT
        IP_ADDRESS,
        URL,
        TIMESTAMP
    FROM RAW_VALUES_CLICKS
    WHERE IP_COUNT = 1
    PARTITION BY IP_ADDRESS;
```

