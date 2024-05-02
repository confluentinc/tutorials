<!-- title: How to join two tables in ksqlDB -->
<!-- description: In this tutorial, learn how to join two tables in ksqlDB, with step-by-step instructions and supporting code. -->

# How to join two tables in ksqlDB

Consider that you have two tables of reference data in Kafka topics, and you want to join them on a common key.

## Setup

For this example, let's say you have data about movies in one table, and you want to add additional information like who was the lead actor.

First, here's your table containing movie information:

```sql
CREATE TABLE movies (
        title VARCHAR PRIMARY KEY,
        id INT,
        release_year INT
    ) WITH (
      KAFKA_TOPIC='movies',
      PARTITIONS=1,
      VALUE_FORMAT='JSON'
      );
```

And here's a table containing additional movie data with information on actors:

```sql
 CREATE TABLE lead_actor (
     title VARCHAR PRIMARY KEY,
     actor_name VARCHAR
   ) WITH (
     KAFKA_TOPIC='lead_actors',
     PARTITIONS=1,
     VALUE_FORMAT='JSON'
   );
```

For the join between these tables, you create another table containing your desired information:

```sql
CREATE TABLE MOVIES_ENRICHED AS
    SELECT m.id, m.title, m.release_year, l.actor_name
    FROM movies m
    INNER JOIN lead_actor l
    ON m.title = l.title;
```

