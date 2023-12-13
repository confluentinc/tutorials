CREATE STREAM TEMPERATURE_READINGS_RAW (eventTime BIGINT, temperature INT)
    WITH (kafka_topic='deviceEvents', value_format='avro', partitions=1);
