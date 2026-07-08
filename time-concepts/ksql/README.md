<!-- title: How to implement event-time semantics in ksqlDB -->
<!-- description: In this tutorial, learn how to implement event-time semantics in ksqlDB, with step-by-step instructions and supporting code. -->

# How to implement event-time semantics in ksqlDB

By default, time-based aggregations in ksqlDB (tumbling windows, hopping windows, etc.) operate on the timestamp in the record metadata, which could be either 'CreateTime' (the producer system time) or 'LogAppendTime' (the broker system time), depending on the `message.timestamp.type` topic configuration value. 'CreateTime' may help with event-time semantics, but in some use cases, the desired event time is a timestamp embedded inside the record payload itself.

For example, consider a topic of temperature sensor readings that contains the temperature and timestamp of the reading. In order to achieve event-time semantics, we specify a field in the record payload as the `TIMESTAMP` attribute when defining the stream:

```sql
CREATE STREAM temperature_event_time (temp DOUBLE, event_time BIGINT)
  WITH (KAFKA_TOPIC='temperature-event-time',
        PARTITIONS=1,
        VALUE_FORMAT='AVRO',
        TIMESTAMP='event_time');
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

Run the following SQL statements to create two streams: one that uses log-time semantics, and one that uses event-time semantics:

```sql
CREATE STREAM temperature_log_time (temp DOUBLE, event_time BIGINT)
WITH (KAFKA_TOPIC='temperature-log-time',
      PARTITIONS=1,
      VALUE_FORMAT='AVRO');
```

```sql
CREATE STREAM temperature_event_time (temp DOUBLE, event_time BIGINT)
    WITH (KAFKA_TOPIC='temperature-event-time',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO',
          TIMESTAMP='event_time');
```

Next, insert the same event into each stream:

```sql
INSERT INTO temperature_log_time   (temp, event_time) VALUES (100.98, 1673560175029);
INSERT INTO temperature_event_time (temp, event_time) VALUES (100.98, 1673560175029);
```

Now if you query the `temperature_log_time` stream and include the system column `ROWTIME` that gets used for time-based aggregations, you'll see that the `ROWTIME` is the current time and not the time in the event payload. Note that we first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

SELECT *, ROWTIME
FROM temperature_log_time
EMIT CHANGES;
```

The query output will show different timestamps, e.g.:

```plaintext
+-----------------------------+-----------------------------+-----------------------------+
|TEMP                         |EVENT_TIME                   |ROWTIME                      |
+-----------------------------+-----------------------------+-----------------------------+
|100.98                       |1673560175029                |1727450542650                |
+-----------------------------+-----------------------------+-----------------------------+
```

Now query the `temperature_event_time` stream and include the system column `ROWTIME`:

```sql
SELECT *, ROWTIME
FROM temperature_event_time
EMIT CHANGES;
```  

The system column `ROWTIME` matches the timestamp in the event:

```plaintext
+-----------------------------+-----------------------------+-----------------------------+
|TEMP                         |EVENT_TIME                   |ROWTIME                      |
+-----------------------------+-----------------------------+-----------------------------+
|100.98                       |1673560175029                |1673560175029                |
+-----------------------------+-----------------------------+-----------------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
