CREATE STREAM movies_avro (MOVIE_ID BIGINT KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='avro-movies',
          PARTITIONS=1,
          VALUE_FORMAT='avro');

CREATE STREAM movies_proto
    WITH (KAFKA_TOPIC='proto-movies',
          PARTITIONS=1,
          VALUE_FORMAT='protobuf') AS
    SELECT * FROM movies_avro;
