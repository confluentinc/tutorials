SELECT title,
       rating_count,
       TIMESTAMPTOSTRING(window_start, 'yyy-MM-dd HH:mm:ss', 'UTC') as window_start,
       TIMESTAMPTOSTRING(window_end, 'yyy-MM-dd HH:mm:ss', 'UTC') as window_end
FROM rating_count
EMIT CHANGES
LIMIT 11;
