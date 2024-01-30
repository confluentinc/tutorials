# How to change the serialization format of messages with ksqlDB

If you have a stream of Avro-formatted events in a Kafka topic, it's trivial to convert the events to Protobuf format by using a [CREATE STREAM AS SELECT](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/create-stream-as-select/) (CSAS) statement to populate the Protobuf-formatted stream with values from the Avro-formatted stream.

For example, suppose that you have a stream with Avro-formatted values that represent movie releases:

```sql
CREATE STREAM movies_avro (MOVIE_ID BIGINT KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='avro-movies',
          PARTITIONS=1,
          VALUE_FORMAT='avro');
```

Then the analogous stream with Protobuf-formatted values can be created and populated as follows:

```sql
CREATE STREAM movies_proto
    WITH (KAFKA_TOPIC='proto-movies', VALUE_FORMAT='protobuf') AS
    SELECT * FROM movies_avro;
```
