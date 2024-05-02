<!-- title: How to aggregate over hopping windows with ksqlDB -->
<!-- description: In this tutorial, learn how to aggregate over hopping windows with ksqlDB, with step-by-step instructions and supporting code. -->

# How to aggregate over hopping windows with ksqlDB

A hopping window is a fixed-size window with an advance that is smaller than the window size. Due to that fact the advance is smaller than the window size, hopping windows contain overlapping results.

## Setup

Imagine you have a topic of temperature readings. The first step is to create a stream over this topic:

```sql
CREATE STREAM TEMPERATURE_READINGS (ID VARCHAR KEY, TIMESTAMP VARCHAR, READING BIGINT)
    WITH (KAFKA_TOPIC = 'TEMPERATURE_READINGS',
          VALUE_FORMAT = 'JSON',
          TIMESTAMP = 'TIMESTAMP',
          TIMESTAMP_FORMAT = 'yyyy-MM-dd HH:mm:ss',
          PARTITIONS = 1);
```

Now you want to generate an average temperature reading every 5 minutes over the last 10 minutes of data:

```sql
CREATE TABLE AVERAGE_TEMPS AS
SELECT
        ID AS KEY,
        AS_VALUE(ID) AS ID,
        SUM(READING)/COUNT(READING) AS AVG_READING
    FROM TEMPERATURE_READINGS
      WINDOW HOPPING (SIZE 10 MINUTES, ADVANCE BY 5 MINUTES)
    GROUP BY ID;
```

ksqlDB automatically includes the window start (`WINDOWSTART`) and end (`WINDOWEND`) timestamps as columns in the result.  If you wanted to change to format you could add something
like this to your select statement:
```sql
TIMESTAMPTOSTRING(WINDOWSTART, 'HH:mm:ss', 'UTC') AS START_PERIOD,
TIMESTAMPTOSTRING(WINDOWEND, 'HH:mm:ss', 'UTC') AS END_PERIOD,
```