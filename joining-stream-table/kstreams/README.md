# KStream - KTable joins in Kafka Streams

Suppose you have a set of movies that have been released and a stream of ratings from moviegoers about how entertaining they are. In this tutorial, we'll write a program that joins each rating with content about the movie.


First you'll create a `KTable` for the reference movie data
```java
KTable<Long, Movie> movieTable = builder.stream(MOVIE_INPUT_TOPIC,
                Consumed.with(Serdes.Long(), movieSerde))
        .peek((key, value) -> LOG.info("Incoming movies key[{}] value[{}]", key, value))
        .toTable(Materialized.with(Serdes.Long(), movieSerde));
```
Here you've started with a `KStream` so you can add a `peek` statement to view the incoming records, then convert it to a table with the `toTable` operator.  Otherwise, you could create the table directly with `StreamBuilder.table`.  Here we are assuming the underlying topic is keyed with the movie id.

Then you'll create your `KStream` of ratings:
```java
 KStream<Long, Rating> ratings = builder.stream(RATING_INPUT_TOPIC,
                        Consumed.with(Serdes.Long(), ratingSerde))
                .map((key, rating) -> new KeyValue<>(rating.id(), rating));
```
We need to have the same id as the table, so we'll use a `KStream.map` operator to set the rating id as the key.  The `Rating` class id uses the same id as the movie.

Now you use a [ValueJoiner]https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/ValueJoiner.html specifying how to construct the joined value of the stream and table:

```java
public class MovieRatingJoiner implements ValueJoiner<Rating, Movie, RatedMovie> {

  public RatedMovie apply(Rating rating, Movie movie) {
    return new RatedMovie(movie.id(), movie.title(), movie.releaseYear(), rating.rating());
  }
}
```
Now, you'll put all this together using a `KStream.join` operation:

```java
 ratings.join(movieTable, joiner, Joined.with(Serdes.Long(),ratingSerde, movieSerde))
                .to(RATED_MOVIES_OUTPUT, Produced.with(Serdes.Long(), ratedMovieSerde));
```
Notice that you're supplying the `Serde` for the key, the stream value and the value of the table via the `Joined` configuration object.







