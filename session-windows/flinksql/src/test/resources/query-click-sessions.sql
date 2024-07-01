SELECT url,
       COUNT(url) AS visited_count,
       window_start,
       window_end
FROM TABLE(SESSION(TABLE clicks PARTITION BY url, DESCRIPTOR(click_ts), INTERVAL '2' MINUTES))
GROUP BY url, window_start, window_end;
