package io.confluent.developer;

import org.apache.kafka.streams.kstream.Aggregator;

import java.util.HashMap;
import java.util.Map;

public class LoginAggregator implements Aggregator<String, LoginEvent, LoginRollup> {

  @Override
  public LoginRollup apply(final String appId,
                           final LoginEvent loginEvent,
                           final LoginRollup loginRollup) {
    final String userId = loginEvent.userId();
    final Map<String, Map<String, Long>> allLogins = loginRollup.loginByAppIdAndUserId();
    final Map<String, Long> userLogins = allLogins.computeIfAbsent(appId, key -> new HashMap<>());
    userLogins.compute(userId, (k, v) -> v == null ? 1L : v + 1L);
    return loginRollup;
  }
}
