package io.confluent.developer;

public record LoginEvent(String appId, String userId, long time) {
}
