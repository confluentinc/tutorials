<!-- title: How to rekey a stream with ksqlDB -->
<!-- description: In this tutorial, learn how to rekey a stream with ksqlDB, with step-by-step instructions and supporting code. -->

# How to rekey a stream with ksqlDB

If you have a stream that is either unkeyed (the key is null) or not keyed by the desired field, you can rekey the stream
by issuing a [`CREATE STREAM AS SELECT`](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/create-stream-as-select/)
(CSAS) statement and explicitly specifying the new key with `PARTITION BY`. The new stream can be partitioned by a value or a scalar function.

For example, suppose that you have an unkeyed stream representing movies:

```sql
CREATE STREAM movies (id INT, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='movies', partitions=1, value_format = 'avro');
```

Assume that the `title` field includes both the title and release year, e.g.:

```sql
INSERT INTO movies (id, title, genre) VALUES (294, 'Die Hard::1988', 'action');
```

Then you can rekey by a value (e.g., the `id` field) as follows:

```sql
CREATE STREAM movies_by_id
    WITH (KAFKA_TOPIC='movies_by_id') AS
SELECT *
FROM movies
    PARTITION BY id;
```

Or, you can rekey by function (e.g, the title extracted from the `title` field) as follows:

```sql
CREATE STREAM movies_by_title
    WITH (KAFKA_TOPIC='movies_by_title') AS
SELECT *
FROM movies
    PARTITION BY SPLIT(title, '::')[1];
```
