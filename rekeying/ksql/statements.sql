CREATE STREAM movies (id INT, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='movies', partitions=1, value_format = 'avro');

INSERT INTO movies (id, title, genre) VALUES (294, 'Die Hard::1988', 'action');
INSERT INTO movies (id, title, genre) VALUES (354, 'Tree of Life::2011', 'drama');
INSERT INTO movies (id, title, genre) VALUES (782, 'A Walk in the Clouds::1995', 'romance');
INSERT INTO movies (id, title, genre) VALUES (128, 'The Big Lebowski::1998', 'comedy');

SET 'auto.offset.reset' = 'earliest';

PRINT movies FROM BEGINNING LIMIT 4;

CREATE STREAM movies_by_id
    WITH (KAFKA_TOPIC='movies_by_id') AS
SELECT *
FROM movies
    PARTITION BY id;

PRINT movies_by_id FROM BEGINNING LIMIT 4;

CREATE STREAM movies_by_title
    WITH (KAFKA_TOPIC='movies_by_title') AS
SELECT *
FROM movies
    PARTITION BY SPLIT(title, '::')[1];

PRINT movies_by_title FROM BEGINNING LIMIT 4;
