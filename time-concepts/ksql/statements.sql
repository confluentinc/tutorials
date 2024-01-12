CREATE STREAM temperature_logtime (temp DOUBLE, event_time BIGINT)
WITH (
    kafka_topic = 'temperature-logtime',
    partitions = 1,
    value_format = 'avro'
);

INSERT INTO temperature_logtime (temp, event_time) VALUES (100.98, 1673560175029);

SELECT *, ROWTIME FROM temperature_eventtime EMIT CHANGES;

CREATE STREAM temperature_eventtime (temp DOUBLE, event_time BIGINT)
WITH (
    kafka_topic = 'temperature-eventtime',
    partitions = 1,
    value_format = 'avro',
    timestamp = 'event_time'
);

INSERT INTO temperature_eventtime (temp, event_time) VALUES (100.98, 1673560175029);

SELECT *, ROWTIME FROM temperature_eventtime EMIT CHANGES;
