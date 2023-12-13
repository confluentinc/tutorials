CREATE TABLE rating_count
    WITH (kafka_topic='rating_count') AS
    SELECT title,
           COUNT(*) AS rating_count,
           WINDOWSTART AS window_start,
           WINDOWEND AS window_end
    FROM ratings
    WINDOW TUMBLING (SIZE 6 HOURS)
    GROUP BY title;
