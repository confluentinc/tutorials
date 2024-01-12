# Event-time Semantics in ksqlDB


By default, time-based aggregations in ksqlDB (tumbling windows, hopping windows, etc.) operate on the timestamp in the record metadata, which could be either 'CreateTime' (the producer system time) or 'LogAppendTime' (the broker system time), depending on the `message.timestamp.type` topic configuration value. 'CreateTime' may help with event time semantics, but in some use cases, the desired event time is a timestamp embedded inside the record payload itself.

To demonstrate how to achieve event-time semantics in ksqlDB, consider a topic of temperature sensor readings that contains the temperature and timestamp of the reading. Let's first define the stream _not_ using event-time semantics:

```sql
CREATE STREAM temperature_logtime (temp DOUBLE, event_time BIGINT)
WITH (
    kafka_topic = 'temperature-logtime',
    partitions = 1,
    value_format = 'avro'
);
```

Insert a row of data:

```sql
INSERT INTO temperature_logtime (temp, event_time) VALUES (100.98, 1673560175029);
```

Now if you query this stream and include the system column `ROWTIME` that gets used for time-based aggregations, you'll see that the `ROWTIME` is the current time and not the time in the event payload, e.g.:

```plaintext
ksql> SELECT *, ROWTIME FROM temperature_logtime EMIT CHANGES;
+-----------------------------------------+-----------------------------------------+-----------------------------------------+
|TEMP                                     |EVENT_TIME                               |ROWTIME                                  |
+-----------------------------------------+-----------------------------------------+-----------------------------------------+
|100.98                                   |1673560175029                            |1705078226569                            |
```

Now let's define a second stream that gives us event-time semantics in ksqlDB. To achieve this, we specify a field in the record payload as the `TIMESTAMP` attribute when defining the stream:

```sql
CREATE STREAM temperature_eventtime (temp DOUBLE, event_time BIGINT)
WITH (
    kafka_topic = 'temperature-eventtime',
    partitions = 1,
    value_format = 'avro',
    timestamp = 'event_time'
);
```

Insert the same event as before:

```sql
INSERT INTO temperature_eventtime (temp, event_time) VALUES (100.98, 1673560175029);
```

Now observe that the `ROWTIME` equals the timestamp in the record payload:

```plaintext
ksql> SELECT *, ROWTIME FROM temperature_eventtime EMIT CHANGES;
+-----------------------------------------+-----------------------------------------+-----------------------------------------+
|TEMP                                     |EVENT_TIME                               |ROWTIME                                  |
+-----------------------------------------+-----------------------------------------+-----------------------------------------+
|100.98                                   |1673560175029                            |1673560175029                            |
```
