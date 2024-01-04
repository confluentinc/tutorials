# Add a key to data ingested through Kafka Connect


Kafka Connect is the integration API for Apache Kafka. It enables you to stream data from source systems (such as databases, message queues, SaaS platforms, and flat files) into Kafka, and from Kafka to target systems. When you stream data into Kafka, you often need to set the key correctly for partitioning and application logic reasons. In this example, we have a database containing data about cities, and we want to key the resulting Kafka messages by the city_id field. This tutorial will show you different ways of setting the key correctly.

## Setup

Imagine you have a table in an external database named `cities` with the following schema:
```sql
TABLE cities (city_id INTEGER PRIMARY KEY NOT NULL, name VARCHAR(255), state VARCHAR(255)); 
```

To capture all activity into Kafka, you'll use the [JDBC source connector](https://docs.confluent.io/kafka-connectors/jdbc/current/source-connector/overview.html#jdbc-source-connector-for-cp).  ksqlDB natively integrates with Connect by either [communicating with an external Connect 
cluster or by running Connect embedded](https://docs.ksqldb.io/en/0.10.2-ksqldb/tutorials/embedded-connect/#when-to-use-embedded-connect) 
within the ksqlDB server process. 

In this tutorial, we'll show the embedded Connect approach.  To start the JDBC connector from the ksqlDB CLI or from the Editor tab on Confluent Cloud, 
you'll run this SQL command:

```sql
CREATE SOURCE CONNECTOR IF NOT EXISTS JDBC_SOURCE_DB_CONNECTOR WITH (
    'connector.class'= 'io.confluent.connect.jdbc.JdbcSourceConnector',
    'connection.url'= '<DB URL>',
    'connection.user'= '<DB USERNAME>',
    'connection.password'= '<DB PASSWORD>',
    'mode'= 'incrementing',
    'incrementing.column.name'= 'city_id',
    'topic.prefix'= 'postgres_',
    'transforms'= 'copyFieldToKey,extractKeyFromStruct,removeKeyFromValue',
    'transforms.copyFieldToKey.type'= 'org.apache.kafka.connect.transforms.ValueToKey',
    'transforms.copyFieldToKey.fields'= 'city_id',
    'transforms.extractKeyFromStruct.type'= 'org.apache.kafka.connect.transforms.ExtractField$Key',
    'transforms.extractKeyFromStruct.field'= 'city_id',
    'transforms.removeKeyFromValue.type'= 'org.apache.kafka.connect.transforms.ReplaceField$Value',
    'transforms.removeKeyFromValue.blacklist'= 'city_id',
    'key.converter' = 'org.apache.kafka.connect.converters.IntegerConverter'
);
```

Here are the main points to consider: 

The `transforms` entry specifies three [Single Message Transforms](https://docs.confluent.io/platform/current/connect/transforms/overview.html)(SMTs) that
 set the key to the value of the `city_id` field. They run in the order as listed:

- `copyFieldToKey` sets the key to a struct containing the `city_id` field from the value.

- `extractKeyFromStruct` sets the key to just the `city_id` field of the struct set by the previous step.

- `removeKeyFromValue removes` the `city_id` from the message value, as it’s now stored in the message key.

Since the key is an integer, we override the default serialization and instead use the `IntegerConverter` for the key field.

You can confirm the connector is running with this statement:
```sql
SHOW CONNECTORS;
```

Then you can create a table in ksqlDB with the primary key of `CITY_ID`:
```sql
CREATE TABLE CITIES (CITY_ID INT PRIMARY KEY) WITH (KAFKA_TOPIC='postgres_cities', VALUE_FORMAT='AVRO');
```

And execute the following to query it:
```sql
SELECT CITY_ID, NAME, STATE FROM CITIES EMIT CHANGES;
```


