package io.confluent.developer;

public record TrackPurchase(long id, String songTitle, long albumId, double price) {
}
