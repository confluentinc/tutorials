# Merging multiple streams into one

In this tutorial, we take a look at the case when you have multiple streams, but you'd like to merge them into one.  We'll use the example of streams of a music catalog to demonstrate.

## Setup

Let's say you have a stream of songs in the rock genre:

```sql
CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
    WITH (kafka_topic='rock_songs', partitions=1, value_format='avro');
```

And another one with classical music:

```sql
CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
    WITH (kafka_topic='classical_songs', partitions=1, value_format='avro');
```

You want to combine them into a single stream where in addition to the `artist` and `title` columns you'll add a `genre`:
```sql
CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='all_songs', partitions=1, value_format='avro');
```

To merge the two streams into the new one, you'll execute statements that will select everything from both streams and insert them into the `all_songs` stream and populate the `genre` column as well:
 
```sql
INSERT INTO all_songs SELECT artist, title, 'rock' AS genre FROM rock_songs;
INSERT INTO all_songs SELECT artist, title, 'classical' AS genre FROM classical_songs;
```
