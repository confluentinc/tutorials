package io.confluent.developer;

import org.apache.kafka.streams.kstream.ValueJoiner;

public class MovieRatingJoiner implements ValueJoiner<Rating, Movie, RatedMovie> {

  public RatedMovie apply(Rating rating, Movie movie) {
    return new RatedMovie(movie.id(), movie.title(), movie.releaseYear(), rating.rating());
  }
}
