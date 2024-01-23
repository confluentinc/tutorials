# How to split a stream of events into substreams with ksqlDB


If you have a stream of events in a Kafka topic and wish to split it into substreams based on a field in each event (a.k.a. content-based routing), you an use ksqlDB's [WHERE](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/quick-reference/#where) clause to accomplish the task.

For example, suppose that you have a stream representing appearances of an actor or actress in a film, with each event also containing the movie genre:

```sql
CREATE STREAM actingevents (name VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC = 'acting-events', PARTITIONS = 1, VALUE_FORMAT = 'AVRO');
```

The following CSAS (`CREATE STREAM AS SELECT`) queries will split the stream into three substreams: one containing drama events, one containing fantasy, and one containing events for all other genres:

```sql
CREATE STREAM actingevents_drama AS
    SELECT name, title
    FROM actingevents
    WHERE genre='drama';

CREATE STREAM actingevents_fantasy AS
    SELECT name, title
    FROM actingevents
    WHERE genre='fantasy';

CREATE STREAM actingevents_other AS
    SELECT name, title, genre
    FROM actingevents
    WHERE genre != 'drama' AND genre != 'fantasy';
```
