package io.confluent.developer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovieRatingJoinerTest {

  @Test
  @DisplayName("Should join movie and rating")
  void testApply() {
    RatedMovie actualRatedMovie;

    Movie treeOfLife  = new Movie("354", "Tree of Life", 2011);
    Rating rating = new Rating("354", 9.8);
    RatedMovie expectedRatedMovie = new RatedMovie("354", "Tree of Life", 2011, 9.8);
    MovieRatingJoiner joiner = new MovieRatingJoiner();
    actualRatedMovie = joiner.apply(rating, treeOfLife);

    assertEquals(actualRatedMovie, expectedRatedMovie);
  }
}