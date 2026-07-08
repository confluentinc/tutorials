<!-- title: How to merge multiple streams with ksqlDB -->
<!-- description: In this tutorial, learn how to merge multiple streams with ksqlDB, with step-by-step instructions and supporting code. -->

# How to merge multiple streams with ksqlDB

In this tutorial, we take a look at the case when you have multiple streams, but you'd like to merge them into one.  
We'll use multiple streams of a music catalog to demonstrate.

## Setup

Let's say you have a stream of songs in the rock genre:

```sql
CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
    WITH (KAFKA_TOPIC='rock_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

And another one with classical music:

```sql
CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
    WITH (KAFKA_TOPIC='classical_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

You want to combine them into a single stream where in addition to the `artist` and `title` columns you'll add a `genre`:
```sql
CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='all_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

To merge the two streams into the new one, you'll execute statements that will select everything from both streams and insert them into the `all_songs` stream and populate the `genre` column as well:
 
```sql
INSERT INTO all_songs SELECT artist, title, 'rock' AS genre FROM rock_songs;
INSERT INTO all_songs SELECT artist, title, 'classical' AS genre FROM classical_songs;
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

Run the following SQL statements to create the `rock_songs`, `classical_songs`, and `all_songs` streams backed by Kafka running in Docker and 
populate the first two with test data.

```sql
CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
    WITH (KAFKA_TOPIC='rock_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

```sql
CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
    WITH (KAFKA_TOPIC='classical_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

```sql
INSERT INTO rock_songs (artist, title) VALUES ('Metallica', 'Fade to Black');
INSERT INTO rock_songs (artist, title) VALUES ('Smashing Pumpkins', 'Today');
INSERT INTO rock_songs (artist, title) VALUES ('Pink Floyd', 'Another Brick in the Wall');
INSERT INTO rock_songs (artist, title) VALUES ('Van Halen', 'Jump');
INSERT INTO rock_songs (artist, title) VALUES ('Led Zeppelin', 'Kashmir');

INSERT INTO classical_songs (artist, title) VALUES ('Wolfgang Amadeus Mozart', 'The Magic Flute');
INSERT INTO classical_songs (artist, title) VALUES ('Johann Pachelbel', 'Canon');
INSERT INTO classical_songs (artist, title) VALUES ('Ludwig van Beethoven', 'Symphony No. 5');
INSERT INTO classical_songs (artist, title) VALUES ('Edward Elgar', 'Pomp and Circumstance');
```

```sql
CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='all_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

Next, run the two `INSERT` statements that will select everything from both streams and insert them into the 
`all_songs` stream while also populating the `genre` column. Note that we first
tell ksqlDB to consume from the beginning of the streams.

```sql
SET 'auto.offset.reset'='earliest';

INSERT INTO all_songs SELECT artist, title, 'rock' AS genre FROM rock_songs;
INSERT INTO all_songs SELECT artist, title, 'classical' AS genre FROM classical_songs;
```

Query the new stream:

```sql
SELECT * FROM all_songs;
```

The query output should look like this:

```plaintext
+-------------------------------+-------------------------------+-------------------------------+
|ARTIST                         |TITLE                          |GENRE                          |
+-------------------------------+-------------------------------+-------------------------------+
|Metallica                      |Fade to Black                  |rock                           |
|Smashing Pumpkins              |Today                          |rock                           |
|Pink Floyd                     |Another Brick in the Wall      |rock                           |
|Van Halen                      |Jump                           |rock                           |
|Led Zeppelin                   |Kashmir                        |rock                           |
|Wolfgang Amadeus Mozart        |The Magic Flute                |classical                      |
|Johann Pachelbel               |Canon                          |classical                      |
|Ludwig van Beethoven           |Symphony No. 5                 |classical                      |
|Edward Elgar                   |Pomp and Circumstance          |classical                      |
+-------------------------------+-------------------------------+-------------------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
