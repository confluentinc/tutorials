SELECT title, genre, movie_start, COUNT(*)
    OVER (
    PARTITION BY genre
    ORDER BY movie_start
    RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND CURRENT ROW
    ) AS genre_count
FROM movie_views;
