CREATE TABLE movies (title VARCHAR PRIMARY KEY, id INT, release_year INT)
    WITH (KAFKA_TOPIC='movies',
        PARTITIONS=1,
        VALUE_FORMAT='JSON');

CREATE TABLE lead_actor (title VARCHAR PRIMARY KEY, actor_name VARCHAR)
    WITH (KAFKA_TOPIC='lead_actors',
        PARTITIONS=1,
        VALUE_FORMAT='JSON');

CREATE TABLE movies_enriched AS
SELECT m.id, m.title, m.release_year, l.actor_name
FROM movies m
         INNER JOIN lead_actor l
         ON m.title = l.title;
