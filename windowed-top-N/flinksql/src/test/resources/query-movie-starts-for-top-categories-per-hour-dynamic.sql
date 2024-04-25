SELECT  *
FROM (
   SELECT *, ROW_NUMBER() OVER (PARTITION BY window_start, window_end ORDER BY category_count DESC ) as hour_rank
      FROM (
            SELECT window_start, window_end, genre, COUNT(*) as category_count
               FROM TABLE(TUMBLE(TABLE movie_views, DESCRIPTOR(movie_start), INTERVAL '1' HOUR))
            GROUP BY window_start, window_end, genre
              )
) WHERE hour_rank = 1 ;
