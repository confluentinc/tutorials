package io.confluent.developer;

public record RatedMovie(String id, String title, int releaseYear, double rating) {
}
