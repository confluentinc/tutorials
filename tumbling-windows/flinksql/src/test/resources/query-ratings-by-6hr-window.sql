SELECT title,
    COUNT(*) AS rating_count,
    AVG(rating) AS avg_rating,
    window_start,
    window_end
FROM TABLE(TUMBLE(TABLE ratings, DESCRIPTOR(ts), INTERVAL '6' HOURS))
GROUP BY title, window_start, window_end;
