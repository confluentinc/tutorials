<!-- title: How to convert a timestamp into a different timezone with ksqlDB -->
<!-- description: In this tutorial, learn how to convert a timestamp into a different timezone with ksqlDB, with step-by-step instructions and supporting code. -->

# How to convert a timestamp into a different timezone with ksqlDB

Suppose you want to create reports from a table and all the timestamps must be in a particular timezone, which happens to be different from the timezone of the Kafka data source. This tutorial shows how you can convert timestamp data into another timezone.

## Setup

You'll start with a stream of temperature readings sourced from a Kafka topic named `deviceEvents`.  The timestamps are in Unix time format of a `long` which is a `BIGINT` in ksqlDB.

```sql
CREATE STREAM TEMPERATURE_READINGS_RAW (eventTime BIGINT, temperature INT)
    WITH (kafka_topic='deviceEvents', value_format='JSON');
```
                    
To covert this column to timestamp format and in a different time zone (`America/Denver`) you'll create a new stream, 
selecting values from the `TEMPERATURE_READINGS_RAW` stream. 

```sql
CREATE STREAM TEMPERATURE_READINGS_TIMESTAMP_CONVERTED AS
SELECT temperature, CONVERT_TZ(FROM_UNIXTIME(eventTime), 'UTC', 'America/Denver') AS EVENTTIME_MT
FROM TEMPERATURE_READINGS_RAW;
```
What you've done here is to first convert `eventTime` from a `BIGINT` to a timestamp with the `FROM_UNIXTIME` function. Then the `CONVERT_TZ` function
uses the result to produce a timestamp in the desired time zone of US Mountain Time.
