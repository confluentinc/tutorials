SELECT ratings.movie_id AS ID, title, release_year, rating
   FROM ratings
   LEFT JOIN movies ON ratings.movie_id = movies.id
   EMIT CHANGES LIMIT 9;
