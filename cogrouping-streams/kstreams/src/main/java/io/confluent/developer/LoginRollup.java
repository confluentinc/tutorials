package io.confluent.developer;

import java.util.Map;

public record LoginRollup(Map<String, Map<String, Long>> loginByAppIdAndUserId) {
}
