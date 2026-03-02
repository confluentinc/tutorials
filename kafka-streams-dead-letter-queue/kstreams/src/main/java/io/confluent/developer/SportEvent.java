package io.confluent.developer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public class SportEvent {

    @JsonProperty("sport")
    private String sport;

    @JsonProperty("ball")
    private Ball ball;

    public SportEvent() {
    }

    public SportEvent(String sport, Ball ball) {
        this.sport = sport;
        this.ball = ball;
    }

    public String getSport() {
        return sport;
    }

    public void setSport(String sport) {
        this.sport = sport;
    }

    public Optional<Ball> getBall() {
        return Optional.ofNullable(ball);
    }

    public void setBall(Ball ball) {
        this.ball = ball;
    }

    @Override
    public String toString() {
        return "SportEvent{" +
                "sport='" + sport + '\'' +
                ", ball=" + ball +
                '}';
    }
}
