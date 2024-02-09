# Joining a stream and a table in ksqlDB

You have events in a Kafka topic and a table of reference data (also known as a lookup table).
Let's see how you can join each event in the stream to a piece of data in the table based on a common key.

## Setup

Let's use the example of a movie rating event stream.  But the stream only contains the movie id, which isn't very
descriptive, so you want to enrich it with some additional information.  So you'll set up join between the stream and a table that contains fact or lookup data.

Here's the movie rating stream:

```sql
 CREATE STREAM ratings (movie_id INT KEY, rating DOUBLE)
    WITH (kafka_topic='ratings', 
          partitions=1, 
          value_format='JSON');

```

And this is the table definition containing the movie reference data:

```sql
CREATE TABLE movies (id INT PRIMARY KEY, title VARCHAR, release_year INT)
    WITH (kafka_topic='movies', 
          partitions=1, 
          value_format='JSON');
```

Note that for a stream-table join to succeed, the primary key of the table must be the key of the stream.
For example, the `movies` primary key `id` matches up with the `ratings` stream key of `movie_id`.

With your stream and table in place you can build a join like this:

```sql
CREATE STREAM rated_movies
    WITH (kafka_topic='rated_movies',
          value_format='avro') AS
    SELECT ratings.movie_id AS id, title, rating
    FROM ratings
    LEFT JOIN movies ON ratings.movie_id = movies.id;
```