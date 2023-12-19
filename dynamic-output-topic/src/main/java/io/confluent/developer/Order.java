package io.confluent.developer;

public record Order(long id, String sku, String name, long quantity) {
}