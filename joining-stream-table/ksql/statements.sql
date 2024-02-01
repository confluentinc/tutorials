CREATE STREAM ratings (MOVIE_ID INT KEY, rating DOUBLE)
    WITH (kafka_topic='ratings',
          partitions=1,
          value_format='JSON');


CREATE TABLE movies (ID INT PRIMARY KEY, title VARCHAR, release_year INT)
    WITH (kafka_topic='movies',
        partitions=1,
        value_format='JSON');


CREATE STREAM rated_movies
    WITH (kafka_topic='rated_movies',
          value_format='avro') AS
SELECT ratings.movie_id as id, title, rating
FROM ratings
         LEFT JOIN movies ON ratings.movie_id = movies.id;

