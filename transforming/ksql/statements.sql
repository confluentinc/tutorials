CREATE STREAM raw_movies (id INT KEY, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='movies', partitions=1, value_format = 'avro');

INSERT INTO raw_movies (id, title, genre) VALUES (294, 'Die Hard::1988', 'action');
INSERT INTO raw_movies (id, title, genre) VALUES (354, 'Tree of Life::2011', 'drama');
INSERT INTO raw_movies (id, title, genre) VALUES (782, 'A Walk in the Clouds::1995', 'romance');
INSERT INTO raw_movies (id, title, genre) VALUES (128, 'The Big Lebowski::1998', 'comedy');

SET 'auto.offset.reset' = 'earliest';

SELECT id,
       SPLIT(title, '::')[1] as title,
       CAST(SPLIT(title, '::')[2] AS INT) AS year,
       genre
FROM raw_movies
EMIT CHANGES;
