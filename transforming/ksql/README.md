<!-- title: How to transform events with ksqlDB scalar functions -->
<!-- description: In this tutorial, learn how to transform events with ksqlDB scalar functions, with step-by-step instructions and supporting code. -->

# How to transform events with ksqlDB scalar functions

If you have a stream of events in a Kafka topic and wish to transform a field in each event, you an use ksqlDB's [scalar functions](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/) or implement your own [scalar UDF](https://docs.ksqldb.io/en/latest/how-to-guides/create-a-user-defined-function/#scalar-functions) if
your needs aren't met by the built-in functions.

## Setup

As a concrete example, consider a stream containing events that represent movies. 

```sql
CREATE STREAM movies (id INT KEY, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='movies',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

Each event has a `title` attribute that combines its title and its release year into a string, e.g., `Inside Out 2::2024`.

## Transform events

Given the stream of movies, we can break the `title` field into separate attributes for the title and release year using the
[SPLIT](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/#split) function. `CAST` is
also used to convert the resulting release year's data type from string to integer.

```sql
SELECT id,
       SPLIT(title, '::')[1] AS title,
       CAST(SPLIT(title, '::')[2] AS INT) AS year,
       genre
FROM movies
EMIT CHANGES;
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

Run the following SQL statements to create the `movies` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM movies (id INT KEY, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='movies',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

```sql
INSERT INTO movies (id, title, genre) VALUES (1, 'Twisters::2024', 'drama');
INSERT INTO movies (id, title, genre) VALUES (2, 'Unfrosted::2024', 'comedy');
INSERT INTO movies (id, title, genre) VALUES (3, 'Family Switch::2023', 'comedy');
```

Next, run the event transformation query to split the `title` field into the actual movie title and release year. Note that we 
first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

SELECT id,
       SPLIT(title, '::')[1] AS title,
       CAST(SPLIT(title, '::')[2] AS INT) AS year,
       genre
FROM movies
EMIT CHANGES;
```

The query output should look like this:

```plaintext
+---------------------+---------------------+---------------------+---------------------+
|ID                   |TITLE                |YEAR                 |GENRE                |
+---------------------+---------------------+---------------------+---------------------+
|1                    |Twisters             |2024                 |drama                |
|2                    |Unfrosted            |2024                 |comedy               |
|3                    |Family Switch        |2023                 |comedy               |
+---------------------+---------------------+---------------------+---------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
