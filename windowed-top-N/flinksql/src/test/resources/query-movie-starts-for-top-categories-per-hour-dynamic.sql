SELECT  *
    FROM (
        SELECT *, ROW_NUMBER() OVER (PARTITION BY window_start, window_end ORDER BY genre ) as hour_rank
       FROM (
         SELECT window_start, window_end, genre
         FROM TABLE(
                 TUMBLE(TABLE movie_views, DESCRIPTOR(movie_start), INTERVAL '1' HOUR)
              )
             GROUP BY window_start, window_end, genre
     )
 ) WHERE hour_rank = 1 ;
