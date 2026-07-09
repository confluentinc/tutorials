<!-- title: How to split a stream of events into substreams with ksqlDB -->
<!-- description: In this tutorial, learn how to split a stream of events into substreams with ksqlDB, with step-by-step instructions and supporting code. -->

# How to split a stream of events into substreams with ksqlDB

If you have a stream of events in a Kafka topic and wish to split it into substreams based on a field in each event 
(a.k.a. content-based routing), you can use ksqlDB's [WHERE](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/quick-reference/#where)
clause to populate new streams.

For example, suppose that you have a stream representing appearances of an actor or actress in a film, with each event also containing the movie genre:

```sql
CREATE STREAM acting_events (name VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='acting-events',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

The following CSAS (`CREATE STREAM AS SELECT`) queries will split the stream into three substreams: one containing drama events, one containing fantasy, and one containing events for all other genres:

```sql
CREATE STREAM acting_events_drama AS
    SELECT name, title
    FROM acting_events
    WHERE genre='drama';

CREATE STREAM acting_events_fantasy AS
    SELECT name, title
    FROM acting_events
    WHERE genre='fantasy';

CREATE STREAM acting_events_other AS
    SELECT name, title, genre
    FROM acting_events
    WHERE genre != 'drama' AND genre != 'fantasy';
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

Run the following SQL statements to create the `acting_events` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM acting_events (name VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='acting-events',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

```sql
INSERT INTO acting_events(name, title, genre) VALUES('Michael Keaton', 'Beetlejuice Beetlejuice', 'fantasy');
INSERT INTO acting_events(name, title, genre) VALUES('Joseph Gordon-Levitt', 'Killer Heat', 'drama');
INSERT INTO acting_events(name, title, genre) VALUES('Jerry Seinfeld', 'Unfrosted', 'comedy');
INSERT INTO acting_events(name, title, genre) VALUES('Cillian Murphy', 'Oppenheimer', 'drama');
INSERT INTO acting_events(name, title, genre) VALUES('Ariana Grande', 'Wicked', 'fantasy');
```

Next, run the following three CSAS queries to populate new topics: one containing drama events, one containing fantasy,
and one containing events for all other genres. Note that we first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

CREATE STREAM acting_events_drama AS
    SELECT name, title
    FROM acting_events
    WHERE genre='drama';

CREATE STREAM acting_events_fantasy AS
    SELECT name, title
    FROM acting_events
    WHERE genre='fantasy';

CREATE STREAM acting_events_other AS
    SELECT name, title, genre
    FROM acting_events
    WHERE genre != 'drama' AND genre != 'fantasy';
```

Query one of the new topics to ensure that events are being routed as expected:

```sql
SELECT *
FROM acting_events_fantasy
EMIT CHANGES;
```

The query output should look like this and only show the fantasy films:

```plaintext
+---------------------------------+---------------------------------+
|NAME                             |TITLE                            |
+---------------------------------+---------------------------------+
|Michael Keaton                   |Beetlejuice Beetlejuice          |
|Ariana Grande                    |Wicked                           |
+---------------------------------+---------------------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
