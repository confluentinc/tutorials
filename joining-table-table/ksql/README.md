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
CREATE TABLE lead_actors (
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
CREATE TABLE movies_enriched AS
    SELECT m.id, m.title, m.release_year, l.actor_name
    FROM movies m
    INNER JOIN lead_actors l
    ON m.title = l.title;
```

## Running the example

### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

### Run the commands

Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

Start ksqlDB and Kafka:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml up -d
```

Next, open the ksqlDB CLI:

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Run the following SQL statements to create the `movies` and `lead_actors` tables backed by Kafka running in Docker and 
populate them with test data.

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

```sql
CREATE TABLE lead_actors (
        title VARCHAR PRIMARY KEY,
        actor_name VARCHAR
    ) WITH (
         KAFKA_TOPIC='lead_actors',
         PARTITIONS=1,
         VALUE_FORMAT='JSON'
    );
```

```sql
INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Twisters', 'Glen Powell');
INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Barbie', 'Ryan Gosling');
INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Oppenheimer', 'Cillian Murphy');
INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Blink Twice', 'Channing Tatum');

INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (48, 'Twisters', 2024);
INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (294, 'Barbie', 2023);
INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (128, 'Oppenheimer', 2024);
INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (42, 'Blink Twice', 2024);
```

Finally, run the table-table join query and land the results in a new `movies_enriched` table.

```sql
CREATE TABLE movies_enriched AS
    SELECT m.id, m.title, m.release_year, l.actor_name
    FROM movies m
    INNER JOIN lead_actors l
    ON m.title = l.title;
```

Query the new table:

```sql
SELECT * FROM movies_enriched;
```

The query output should look like this:

```plaintext
+-------------------+-------------------+-------------------+-------------------+
|M_TITLE            |ID                 |RELEASE_YEAR       |ACTOR_NAME         |
+-------------------+-------------------+-------------------+-------------------+
|Barbie             |294                |2023               |Ryan Gosling       |
|Blink Twice        |42                 |2024               |Channing Tatum     |
|Oppenheimer        |128                |2024               |Cillian Murphy     |
|Twisters           |48                 |2024               |Glen Powell        |
+-------------------+-------------------+-------------------+-------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
