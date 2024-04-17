SELECT title, genre, num_views, category_rank
FROM (
         SELECT *,
                ROW_NUMBER() OVER (PARTITION BY genre ORDER BY num_views DESC) as category_rank
         FROM movie_views
     )
WHERE category_rank <= 3;