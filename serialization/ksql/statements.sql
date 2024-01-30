CREATE STREAM movies_avro (MOVIE_ID BIGINT KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='avro-movies',
          PARTITIONS=1,
          VALUE_FORMAT='avro');

INSERT INTO movies_avro (MOVIE_ID, title, release_year) VALUES (1, 'Lethal Weapon', 1992);
INSERT INTO movies_avro (MOVIE_ID, title, release_year) VALUES (2, 'Die Hard', 1988);
INSERT INTO movies_avro (MOVIE_ID, title, release_year) VALUES (3, 'Predator', 1997);

CREATE STREAM movies_proto
    WITH (KAFKA_TOPIC='proto-movies', VALUE_FORMAT='protobuf') AS
SELECT * FROM movies_avro;
