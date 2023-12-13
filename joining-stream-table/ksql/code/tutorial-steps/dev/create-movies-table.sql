CREATE TABLE movies (ID INT PRIMARY KEY, title VARCHAR, release_year INT)
    WITH (kafka_topic='movies', partitions=1, value_format='avro');
