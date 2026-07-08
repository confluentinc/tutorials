<!-- title: How to change the serialization format of messages with ksqlDB -->
<!-- description: In this tutorial, learn how to change the serialization format of messages with ksqlDB, with step-by-step instructions and supporting code. -->

# How to change the serialization format of messages with ksqlDB

If you have a stream of Avro-formatted events in a Kafka topic, it's trivial to convert the events to Protobuf format by using a [CREATE STREAM AS SELECT](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/create-stream-as-select/) (CSAS) statement to populate the Protobuf-formatted stream with values from the Avro-formatted stream.

For example, suppose that you have a stream with Avro-formatted values that represent movie releases:

```sql
CREATE STREAM movies_avro (movie_id BIGINT KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='movies_avro',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

Then the analogous stream with Protobuf-formatted values can be created and populated as follows:

```sql
CREATE STREAM movies_proto
    WITH (KAFKA_TOPIC='movies_proto', VALUE_FORMAT='PROTOBUF') AS
    SELECT * FROM movies_avro;
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

Run the following SQL statements to create the `movies_avro` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM movies_avro (movie_id BIGINT KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='movies_avro',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

```sql
INSERT INTO movies_avro (movie_id, title, release_year) VALUES (1, 'Lethal Weapon', 1992);
INSERT INTO movies_avro (movie_id, title, release_year) VALUES (2, 'The Batman', 2022);
INSERT INTO movies_avro (movie_id, title, release_year) VALUES (3, 'Beetlejuice Beetlejuice', 2024);
```

Finally, run the `CREATE STREAM AS SELECT` query to create an analogous stream with Protobuf-formatted values.
Note that we first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

CREATE STREAM movies_proto
    WITH (KAFKA_TOPIC='movies_proto', VALUE_FORMAT='PROTOBUF') AS
    SELECT * FROM movies_avro;
```

Query the new topic and observe the same events as in the source topic:

```sql
SELECT *
FROM movies_proto;
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
