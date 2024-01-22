# How to split a stream of events into substreams with ksqlDB


If you have a stream of events in a Kafka topic and wish to split it into substreams based on a field in each event (a.k.a. content-based routing), you an use ksqlDB's [WHERE](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/quick-reference/#where) clause to accomplish the task.

For example, suppose that you have a Kafka topic representing appearances of an actor or actress in a film, with each event also containing the movie genre. The following CSAS (`CREATE STREAM AS SELECT`) queries will split the stream into three substreams: one containing drama events, one containing fantasy, and one containing events for all other genres:

```sql
CREATE STREAM actingevents_drama AS
    SELECT NAME, TITLE
    FROM ACTINGEVENTS
    WHERE GENRE='drama';

CREATE STREAM actingevents_fantasy AS
    SELECT NAME, TITLE
    FROM ACTINGEVENTS
    WHERE GENRE='fantasy';

CREATE STREAM actingevents_other AS
    SELECT NAME, TITLE, GENRE
    FROM ACTINGEVENTS
    WHERE GENRE != 'drama' AND GENRE != 'fantasy';
```