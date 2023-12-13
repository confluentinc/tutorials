package io.confluent.developer;

import org.apache.kafka.streams.kstream.ValueJoiner;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.avro.RatedMovie;
import io.confluent.developer.avro.Rating;

public class MovieRatingJoiner implements ValueJoiner<Rating, Movie, RatedMovie> {

  public RatedMovie apply(Rating rating, Movie movie) {
    return RatedMovie.newBuilder()
        .setId(movie.getId())
        .setTitle(movie.getTitle())
        .setReleaseYear(movie.getReleaseYear())
        .setRating(rating.getRating())
        .build();
  }
}
