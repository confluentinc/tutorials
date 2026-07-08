<!-- title: How to handle heterogeneous JSON with ksqlDB -->
<!-- description: In this tutorial, learn how to handle heterogeneous JSON with ksqlDB, with step-by-step instructions and supporting code. -->

# How to handle heterogeneous JSON with ksqlDB

Suppose you have a topic with records formatted in JSON, but not all the records have the same structure and value types. 
In this tutorial, we'll demonstrate how to work with JSON of different structures.

## Set Up

For context, imagine you have three different JSON formats in a Kafka topic:

```json
  "JSONType1": {
    "fieldA": "some data",
    "numberField": 1.001,
    "oneOnlyField": "more data", 
    "randomField": "random data"
  }
```
```json
  "JSONType2": {
    "fieldA": "data",
    "fieldB": "b-data",
    "numberField": 98.6 
  }
```
```json
  "JSONType3": {
    "fieldA": "data",
    "fieldB": "b-data",
    "numberField": 98.6,
    "fieldC": "data",
    "fieldD": "D-data"    
  }
```

From these three different JSON structures you want to extract `oneOnlyField`, `numberField`, and `fieldD` from `JSONType`, `JSONType2`, and `JSONType3` respectively.

Your first step is to create a stream and use a `VARCHAR` keyword to define the outermost element of the JSON types.

```sql
CREATE STREAM data_stream (
    JSONType1 VARCHAR,
    JSONType2 VARCHAR,
    JSONType3 VARCHAR
) WITH (KAFKA_TOPIC='data_stream',
        VALUE_FORMAT='JSON',
        PARTITIONS=1);
```

Then you can access the fields using the `EXTRACTJSONFIELD` keyword and cast into the appropriate types by selecting from `data_stream`:

```sql
SELECT EXTRACTJSONFIELD (JSONType1, '$.oneOnlyField') AS special_info,
       CAST(EXTRACTJSONFIELD (JSONType2, '$.numberField') AS DOUBLE) AS runfld,
       EXTRACTJSONFIELD (JSONType3, '$.fieldD') AS description
FROM data_stream
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

Create the `data_stream` topic:

```shell
docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --create --topic data_stream
```

Open a console producer:

```shell
docker exec -it broker kafka-console-producer --bootstrap-server localhost:29092 --topic data_stream
```

Ever the following four events at the prompt:

```json
{ "JSONType1": { "fieldA": "some data", "numberField": 1.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data" }}
{ "JSONType1": { "fieldA": "some data", "numberField": 2.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 99.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data-2" }}
{ "JSONType1": { "fieldA": "some data", "numberField": 3.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 100.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data-3" }}
{ "JSONType1": { "fieldA": "some data", "numberField": 4.001, "oneOnlyField": "more data", "randomField": "random data" }, "JSONType2": { "fieldA": "data", "fieldB": "b-data", "numberField": 101.6 }, "JSONType3": { "fieldA": "data", "fieldB": "b-data", "numberField": 98.6, "fieldC": "data", "fieldD": "D-data-4" }}
```
Next, open the ksqlDB CLI:

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Enter the following statement. This will create a stream backed by the `data_stream` topic.

```sql
CREATE STREAM data_stream (
    JSONType1 VARCHAR,
    JSONType2 VARCHAR,
    JSONType3 VARCHAR
) WITH (KAFKA_TOPIC='data_stream',
        VALUE_FORMAT='JSON',
        PARTITIONS=1);
```

Now you can access the fields using the `EXTRACTJSONFIELD` function. Note that we first tell ksqlDB to consume from the beginning of the stream.

```sql
SET 'auto.offset.reset'='earliest';

SELECT EXTRACTJSONFIELD (JSONType1, '$.oneOnlyField') AS special_info,
       CAST(EXTRACTJSONFIELD (JSONType2, '$.numberField') AS DOUBLE) AS runfld,
       EXTRACTJSONFIELD (JSONType3, '$.fieldD') AS description
FROM data_stream
EMIT CHANGES;
```

The query output should look like this:

```plaintext
+------------------------+------------------------+------------------------+
|SPECIAL_INFO            |RUNFLD                  |DESCRIPTION             |
+------------------------+------------------------+------------------------+
|more data               |98.6                    |D-data                  |
|more data               |99.6                    |D-data-2                |
|more data               |100.6                   |D-data-3                |
|more data               |101.6                   |D-data-4                |
+------------------------+------------------------+------------------------+
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
