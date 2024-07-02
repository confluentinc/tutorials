<!-- title: How to join a KStream and a KStream in Kafka Streams -->
<!-- description: In this tutorial, learn how to join a KStream and a KStream in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to join a KStream and a KStream in Kafka Streams

Suppose you have a stream of movies that have been released and a stream of ratings from moviegoers about how entertaining they are. In this tutorial, we'll write a program that joins each rating with content about the movie.

First you'll create a `KStream` for the recently released movies:
```java
 KStream<Long, Movie> movieStream = builder.stream(MOVIE_INPUT_TOPIC,
                Consumed.with(Serdes.Long(), movieSerde))
        .peek((key, value) -> LOG.info("Incoming movies key[{}] value[{}]", key, value));
```
Here you've started with a `KStream` with a `peek` statement to view the incoming records. We assume that the underlying topic is keyed on the movie ID.

Then you'll create your `KStream` of ratings:
```java
 KStream<Long, Rating> ratings = builder.stream(RATING_INPUT_TOPIC,
                        Consumed.with(Serdes.Long(), ratingSerde))
                .map((key, rating) -> new KeyValue<>(rating.id(), rating));
```
We need to have the same ID as the movie stream, so we'll use a `KStream.map` operator to set the rating ID as the key.  The `Rating` class ID uses the same ID as the movie.

Now you use a [ValueJoiner](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/ValueJoiner.html) specifying how to construct the joined value of both streams:

```java
public class MovieRatingJoiner implements ValueJoiner<Rating, Movie, RatedMovie> {

  public RatedMovie apply(Rating rating, Movie movie) {
    return new RatedMovie(movie.id(), movie.title(), movie.releaseYear(), rating.rating());
  }
}
```

You'll also create a [JoinWindows](https://www.javadoc.io/doc/org.apache.kafka/kafka-streams/latest/org/apache/kafka/streams/kstream/JoinWindows.html) instance which specifies the maximum time difference between records to complete the join. 
```java
 JoinWindows joinWindows = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(10));
```
Here, you're using the [JoinWindows.ofTimeDifferenceWithNoGrace](https://www.javadoc.io/doc/org.apache.kafka/kafka-streams/latest/org/apache/kafka/streams/kstream/JoinWindows.html#ofTimeDifferenceWithNoGrace-java.time.Duration-) method which means Kafka Streams will drop any [out-of-order records](https://kafka.apache.org/37/documentation/streams/core-concepts#streams_out_of_ordering) after the window period passes and they won't be available for joining.

Now, you'll put all this together using a `KStream.join` operation:

```java
 ratings.join(movieStream, joiner, joinWindows, StreamJoined.with(Serdes.Long(),ratingSerde, movieSerde))
        .to(RATED_MOVIES_OUTPUT,
            Produced.with(Serdes.Long(), ratedMovieSerde));
```
Notice that you're supplying the `Serde` for the key, the stream value and the value of the other stream via the `StreamJoined` configuration object.