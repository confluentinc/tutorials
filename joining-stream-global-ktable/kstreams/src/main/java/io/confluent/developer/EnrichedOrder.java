package io.confluent.developer;

public record EnrichedOrder(String orderId, String productId, String productName) {
}
