<!-- title: How to convert a timestamp into a different time zone with ksqlDB -->
<!-- description: In this tutorial, learn how to convert a timestamp into a different time zone with ksqlDB, with step-by-step instructions and supporting code. -->

# How to convert a timestamp into a different time zone with ksqlDB

Suppose you want to create reports from a table and all the timestamps must be in a particular time zone, which happens to be different from the timezone of the Kafka data source. This tutorial shows how you can convert timestamp data into another timezone.

## Setup

You'll start with a stream of temperature readings sourced from a Kafka topic named `device-events`.  The timestamps are in Unix time format of a `long` which is a `BIGINT` in ksqlDB.

```sql
CREATE STREAM temperature_readings_raw (event_time BIGINT, temperature INT)
    WITH (KAFKA_TOPIC='device-events',
          VALUE_FORMAT='JSON');
```

## Convert to a different time zone

In order to convert this column to timestamp format and in a particular time zone (`America/Denver`), first convert
`event_time` from a `BIGINT` to a timestamp with the `FROM_UNIXTIME` function. Then the `CONVERT_TZ` function
uses the result to produce a timestamp in the desired time zone.

```sql
SELECT temperature,CONVERT_TZ(FROM_UNIXTIME(event_time), 'UTC', 'America/Denver') AS event_time_mt
FROM temperature_readings_raw
EMIT CHANGES;
```

## Running the example

### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

### Run the commands

Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

Start ksqlDB and Kafka:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml up -d
```

Next, open the ksqlDB CLI:

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Run the following SQL statements to create the `temperature_readings_raw` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM temperature_readings_raw (event_time BIGINT, temperature INT)
    WITH (KAFKA_TOPIC='device-events',
          PARTITIONS=1,
          VALUE_FORMAT='JSON');
```

```sql
INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615566394751, 100);
INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615566401534, 132);
INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567732840, 144);
INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567735866, 103);
INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567736875, 102);
INSERT INTO temperature_readings_raw (event_time, temperature) VALUES (1615567738890, 101);
```

Finally, run the timestamp conversion query. Note that we first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

SELECT temperature,
       CONVERT_TZ(FROM_UNIXTIME(event_time), 'UTC', 'America/Denver') AS event_time_mt
FROM temperature_readings_raw
EMIT CHANGES;
```

The query output should look like this:

```plaintext
+-------------------------------------+-------------------------------------+
|TEMPERATURE                          |EVENT_TIME_MT                        |
+-------------------------------------+-------------------------------------+
|100                                  |2021-03-12T09:26:34.751              |
|132                                  |2021-03-12T09:26:41.534              |
|144                                  |2021-03-12T09:48:52.840              |
|103                                  |2021-03-12T09:48:55.866              |
|102                                  |2021-03-12T09:48:56.875              |
|101                                  |2021-03-12T09:48:58.890              |
+-------------------------------------+-------------------------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
