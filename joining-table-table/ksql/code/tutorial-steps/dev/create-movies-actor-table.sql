CREATE TABLE lead_actor (
     title VARCHAR PRIMARY KEY,
     actor_name VARCHAR
   ) WITH (
     KAFKA_TOPIC='lead_actors',
     PARTITIONS=1,
     VALUE_FORMAT='avro'
   );
