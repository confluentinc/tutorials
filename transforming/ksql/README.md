# Event Transformation in ksqlDB


If you have a stream of events in a Kafka topic and wish to transform a field in each event, you an use ksqlDB's [scalar functions](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/) or implement your own [scalar UDF](https://docs.ksqldb.io/en/latest/how-to-guides/create-a-user-defined-function/#scalar-functions) if
your needs aren't met by the built-in functions.

As a concrete example, consider a topic with events that represent movies. Each event has a single attribute that combines its title and its release year into a string. The following ksqlDB query uses the string [SPLIT](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/#split) function to create events in a new topic with title and release date turned into their own attributes.

## Setup

First we need to create a stream of movies. This line of ksqlDB DDL creates a stream and its underlying Kafka topic to represent 
a stream of movies. If the topic already exists, then ksqlDB simply registers it as the source of data underlying the new stream. 
The stream has three fields: `id`, the movie ID; `title`, the title with the year appended; and `genre`, movie's genre.

```sql
CREATE STREAM raw_movies (id INT KEY, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='movies', partitions=1, value_format = 'avro');
```

## Transform events

Given the stream of movies, break the `title` field into separate attributes for the title and release year using the `SPLIT` function. `CAST` is also used to convert the resulting release year's data type from string to integer.

```sql
SELECT id,
       SPLIT(title, '::')[1] as title,
       CAST(SPLIT(title, '::')[2] AS INT) AS year,
       genre
FROM raw_movies
EMIT CHANGES;
```
