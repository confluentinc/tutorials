CREATE STREAM movies_json (MOVIE_ID BIGINT KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='json-movies',
          PARTITIONS=1,
          VALUE_FORMAT='json');
