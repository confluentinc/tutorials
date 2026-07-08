<!-- title: How to update the number of partitions of a Kafka topic with ksqlDB -->
<!-- description: In this tutorial, learn how to update the number of partitions of a Kafka topic with ksqlDB. -->

# How to update the number of partitions of a Kafka topic with ksqlDB

Imagine you want to change the partitions of your Kafka topic. You can use a streaming transformation to automatically stream all the messages from the original topic into a new Kafka topic that has the desired number of partitions.

## Setup

To accomplish this transformation, first create a stream based on the original topic:

```sql
CREATE STREAM s1 (k VARCHAR KEY, v VARCHAR)
    WITH (KAFKA_TOPIC='topic',
          VALUE_FORMAT='JSON');
```

Then, create a second stream that reads everything from the original topic and puts into a new topic with the desired number of partitions:

```sql
CREATE STREAM s2
    WITH (KAFKA_TOPIC='topic2',
          VALUE_FORMAT='JSON',
          PARTITIONS=2) AS
    SELECT *
    FROM s1
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

Run the following SQL statements to create the `s1` stream backed by Kafka running in Docker and populate it with test data.

```sql
CREATE STREAM s1 (k VARCHAR KEY, v VARCHAR)
    WITH (KAFKA_TOPIC='topic1',
          PARTITIONS=1,
          VALUE_FORMAT='JSON');
```

```sql
INSERT INTO s1 (k, v) VALUES ('hello', 'world');
INSERT INTO s1 (k, v) VALUES ('foo', 'bar');
INSERT INTO s1 (k, v) VALUES ('bar', 'baz');
```

Next, run the `CREATE STREAM AS SELECT` query to populate a new topic, `topic2` with the same events in `topic1` but having 2 partitions.

```sql
SET 'auto.offset.reset'='earliest';

CREATE STREAM s2
    WITH (KAFKA_TOPIC='topic2',
          VALUE_FORMAT='JSON',
          PARTITIONS=2) AS
    SELECT *
    FROM s1
    EMIT CHANGES;
```

Observe the expected number of partitions when you run the `kafka-topics` command in the broker container:

```shell
docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --describe --topic topic1
```

```shell
docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --describe --topic topic2
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```