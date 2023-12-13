CREATE STREAM clicks (ip VARCHAR, url VARCHAR, timestamp VARCHAR)
WITH (KAFKA_TOPIC='clicks',
      TIMESTAMP='timestamp',
      TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
      PARTITIONS=1,
      VALUE_FORMAT='Avro');
