CREATE STREAM raw_movies (ID INT KEY, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='movies', partitions=1, value_format = 'avro');

CREATE STREAM movies WITH (kafka_topic = 'parsed_movies', partitions = 1) AS
    SELECT id,
           split(title, '::')[1] as title,
           CAST(split(title, '::')[2] AS INT) AS year,
           genre
    FROM raw_movies;
