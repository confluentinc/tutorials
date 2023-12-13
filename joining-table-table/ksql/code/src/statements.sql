CREATE TABLE movies (TITLE VARCHAR PRIMARY KEY, id INT, release_year INT)
             WITH (KAFKA_TOPIC='movies', 
                   PARTITIONS=1, 
                   VALUE_FORMAT='avro');

CREATE TABLE lead_actor (TITLE VARCHAR PRIMARY KEY, actor_name VARCHAR)
             WITH (KAFKA_TOPIC='lead_actors', 
                   PARTITIONS=1, 
                   VALUE_FORMAT='avro');

CREATE TABLE MOVIES_ENRICHED AS 
  SELECT M.ID, M.TITLE, M.RELEASE_YEAR, L.ACTOR_NAME 
  FROM MOVIES M 
  INNER JOIN LEAD_ACTOR L 
  ON M.TITLE = L.TITLE;
