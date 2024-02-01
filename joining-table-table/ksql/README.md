# Joining two tables in ksqlDB

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
    SELECT M.ID, M.TITLE, M.RELEASE_YEAR, L.ACTOR_NAME
    FROM MOVIES M
    INNER JOIN LEAD_ACTOR L
    ON M.TITLE = L.TITLE;
```

