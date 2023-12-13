CREATE TABLE movies (
      title VARCHAR PRIMARY KEY,
      id INT,
      release_year INT
    ) WITH (
      KAFKA_TOPIC='movies',
      PARTITIONS=1,
      VALUE_FORMAT='avro'
    );
