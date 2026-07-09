<!-- title: How to aggregate over hopping windows with ksqlDB -->
<!-- description: In this tutorial, learn how to aggregate over hopping windows with ksqlDB, with step-by-step instructions and supporting code. -->

# How to aggregate over hopping windows with ksqlDB

A hopping window is a fixed-size window with an advance that is smaller than the window size. Due to that fact that the
advance is smaller than the window size, hopping windows contains overlapping results (i.e., the same event can be
included in multiple consecutive hopping windows).

## Setup

Imagine you have a topic of temperature readings. The first step is to create a stream over this topic:

```sql
CREATE STREAM temperature_readings (id VARCHAR KEY, timestamp VARCHAR, reading BIGINT)
    WITH (KAFKA_TOPIC='temperature_readings',
          VALUE_FORMAT='JSON',
          TIMESTAMP='TIMESTAMP',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
          PARTITIONS=1);
```

Now you want to generate an average temperature reading every 5 minutes over the last 10 minutes of data:

```sql
CREATE TABLE average_temps AS
    SELECT
        id AS key,
        AS_VALUE(id) AS id,
        SUM(reading) / COUNT(reading) AS avg_reading
    FROM temperature_readings
    WINDOW HOPPING (SIZE 10 MINUTES, ADVANCE BY 5 MINUTES)
    GROUP BY id;
```

KsqlDB automatically includes the window start (`WINDOWSTART`) and end (`WINDOWEND`) timestamps as columns in the result.
If you wanted to change to format you could use the `TIMESTAMPTOSTRING` scalar function in the `SELECT` expression, e.g.:

```sql
TIMESTAMPTOSTRING(WINDOWSTART, 'HH:mm:ss', 'UTC') AS start_period,
TIMESTAMPTOSTRING(WINDOWEND, 'HH:mm:ss', 'UTC') AS end_period,
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

Run the following SQL statements to create the `temperature_readings` stream backed by Kafka running in Docker and populate it with
test data.

```sql
CREATE STREAM temperature_readings (id VARCHAR KEY, timestamp VARCHAR, reading BIGINT)
    WITH (KAFKA_TOPIC='temperature_readings',
          VALUE_FORMAT='JSON',
          TIMESTAMP='TIMESTAMP',
          TIMESTAMP_FORMAT='yyyy-MM-dd HH:mm:ss',
          PARTITIONS=1);
```

```sql
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:15:30', 55);
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:20:30', 50);
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:25:30', 45);
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:30:30', 40);
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:35:30', 45);
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:40:30', 50);
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:45:30', 55);
INSERT INTO temperature_readings (id, timestamp, reading) VALUES ('1', '2020-01-15 02:50:30', 60);
```

Next, run the hopping window query to generate a table of average temperature readings every 5 minutes over the last 10 
minutes of data. Note that we first tell ksqlDB to consume from the beginning of the `temperature_readings` stream.

```sql
SET 'auto.offset.reset'='earliest';

CREATE TABLE average_temps AS
    SELECT
        id AS key,
        AS_VALUE(id) AS id,
        SUM(reading) / COUNT(reading) AS avg_reading
    FROM temperature_readings
    WINDOW HOPPING (SIZE 10 MINUTES, ADVANCE BY 5 MINUTES)
    GROUP BY id;
```

Query the table of average temperatures:

```sql
SELECT * FROM average_temps;
```

The query output should look like this:

```plaintext
+---------------------+---------------------+---------------------+---------------------+---------------------+
|KEY                  |WINDOWSTART          |WINDOWEND            |ID                   |AVG_READING          |
+---------------------+---------------------+---------------------+---------------------+---------------------+
|1                    |1579054200000        |1579054800000        |1                    |55                   |
|1                    |1579054500000        |1579055100000        |1                    |52                   |
|1                    |1579054800000        |1579055400000        |1                    |47                   |
|1                    |1579055100000        |1579055700000        |1                    |42                   |
|1                    |1579055400000        |1579056000000        |1                    |42                   |
|1                    |1579055700000        |1579056300000        |1                    |47                   |
|1                    |1579056000000        |1579056600000        |1                    |52                   |
|1                    |1579056300000        |1579056900000        |1                    |57                   |
|1                    |1579056600000        |1579057200000        |1                    |60                   |
+---------------------+---------------------+---------------------+---------------------+---------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
