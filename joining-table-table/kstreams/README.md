<!-- title: How to join a KTable and a KTable in Kafka Streams -->
<!-- description: In this tutorial, learn how to join a KTable and a KTable in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to join a KTable and a KTable in Kafka Streams

Suppose you have a set of movies that have been released and a stream of ratings from moviegoers about how entertaining they are.  But you're only interested in the latest rating and since the `KTable` is an update stream, you'll want to use a `KTable` for the ratings.   In this tutorial, we'll write a program that joins the latest rating with content about the movie.

First you'll create a `KTable` for the reference movie data:
```java
KTable<Long, Movie> movieTable = builder.stream(MOVIE_INPUT_TOPIC,
                Consumed.with(Serdes.Long(), movieSerde))
        .peek((key, value) -> LOG.info("Incoming movies key[{}] value[{}]", key, value))
        .toTable(Materialized.with(Serdes.Long(), movieSerde));
```
Here you've started with a `KStream` so you can add a `peek` statement to view the incoming records, then convert it to a table with the `toTable` operator.  Otherwise, you could create the table directly with `StreamBuilder.table`. We assume that the underlying topic is keyed on the movie ID.

Then you'll create your `KTable` of ratings:
```java
  KTable<Long, Rating> ratingsTable = builder.stream(RATING_INPUT_TOPIC,
                Consumed.with(Serdes.Long(), ratingSerde))
        .map((key, rating) -> new KeyValue<>(rating.id(), rating))
        .toTable(Materialized.with(Serdes.Long(), ratingSerde));
```
We need to have the same ID as the table, so first we'll use a `KStream.map` operator to set the rating ID as the key, and then use the `KStream.toTable` to get a table.  The `Rating` class ID is the same ID as the movie.

Now you use a [ValueJoiner](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/ValueJoiner.html) specifying how to construct the joined value of the two tables:

```java
public class MovieRatingJoiner implements ValueJoiner<Rating, Movie, RatedMovie> {

  public RatedMovie apply(Rating rating, Movie movie) {
    return new RatedMovie(movie.id(), movie.title(), movie.releaseYear(), rating.rating());
  }
}
```
Now, you'll put all this together using a `KTable.join` operation:

```java
  ratingsTable.join(movieTable, joiner, Materialized.with(Serdes.Long(), ratedMovieSerde))
        .toStream()
        .to(RATED_MOVIES_OUTPUT,
            Produced.with(Serdes.Long(), ratedMovieSerde));
```
Notice that you're supplying the `Serde` for the key, and the value of the joined result via the `Materialized` configuration object.
Also, to produce the joined results to Kafka, we need to convert the `KTable` into a `KStream` vi the `KTable.toStream()` method.