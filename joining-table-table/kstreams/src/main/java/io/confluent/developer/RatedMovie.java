package io.confluent.developer;

public record RatedMovie(long id, String title, int releaseYear, double rating) {
}
