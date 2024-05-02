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
CREATE STREAM DATA_STREAM (
  JSONType1 VARCHAR,
  JSONType2 VARCHAR,
  JSONType3 VARCHAR
  ) WITH (KAFKA_TOPIC='source_data',
       VALUE_FORMAT='JSON',
       PARTITIONS=1);
```

Then you can access the fields using the `EXTRACTJSONFIELD` keyword and cast into the appropriate types by selecting from `DATA_STREAM`:

```sql
CREATE STREAM SUMMARY_REPORTS AS
   SELECT
    EXTRACTJSONFIELD (JSONType1, '$.oneOnlyField') AS SPECIAL_INFO,
    CAST(EXTRACTJSONFIELD (JSONType2, '$.numberField') AS DOUBLE) AS RUNFLD,
    EXTRACTJSONFIELD (JSONType3, '$.fieldD') AS DESCRIPTION
FROM
    DATA_STREAM;
```