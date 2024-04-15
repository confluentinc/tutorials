SELECT
    release_year,
    MIN(total_sales) AS min_total_sales,
    MAX(total_sales) AS max_total_sales
FROM movie_sales
GROUP BY release_year;