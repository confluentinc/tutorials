package io.confluent.developer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class LoginAggregatorTest {

    @Test
    @DisplayName("CoGrouping Multiple Streams Test")
    void cogroupAggregateValuesTest() {
        final LoginAggregator loginAggregator = new LoginAggregator();
        final LoginRollup loginRollup = new LoginRollup(new HashMap<>());

        final String appOne = "app-one";
        final String appTwo = "app-two";
        final String appThree = "app-three";

        final String user1 = "user1";
        final String user2 = "user2";

        loginAggregator.apply(appOne, login(appOne, user1), loginRollup);
        loginAggregator.apply(appTwo, login(appTwo, user1), loginRollup);
        loginAggregator.apply(appThree, login(appThree, user1), loginRollup);

        assertThat(loginRollup.loginByAppIdAndUserId().get(appOne).get(user1), is(1L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appTwo).get(user1), is(1L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appThree).get(user1), is(1L));

        loginAggregator.apply(appOne, login(appOne, user1), loginRollup);
        loginAggregator.apply(appTwo, login(appTwo, user1), loginRollup);

        assertThat(loginRollup.loginByAppIdAndUserId().get(appOne).get(user1), is(2L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appTwo).get(user1), is(2L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appThree).get(user1), is(1L));

        loginAggregator.apply(appOne, login(appOne, user2), loginRollup);
        loginAggregator.apply(appTwo, login(appTwo, user2), loginRollup);
        loginAggregator.apply(appThree, login(appThree, user2), loginRollup);

        loginAggregator.apply(appOne, login(appOne, user1), loginRollup);
        loginAggregator.apply(appTwo, login(appTwo, user1), loginRollup);
        loginAggregator.apply(appThree, login(appThree, user1), loginRollup);

        assertThat(loginRollup.loginByAppIdAndUserId().get(appOne).get(user1), is(3L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appTwo).get(user1), is(3L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appThree).get(user1), is(2L));

        assertThat(loginRollup.loginByAppIdAndUserId().get(appOne).get(user2), is(1L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appTwo).get(user2), is(1L));
        assertThat(loginRollup.loginByAppIdAndUserId().get(appThree).get(user2), is(1L));

    }

    private LoginEvent login(String appId, String userId) {
        return new LoginEvent(appId, userId, System.currentTimeMillis());
    }

}