CREATE STREAM raw_movies (ID INT KEY, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='movies', partitions=1, value_format = 'avro');
