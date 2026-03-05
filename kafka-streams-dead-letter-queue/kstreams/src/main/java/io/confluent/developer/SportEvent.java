package io.confluent.developer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.Map;

public class SportEvent {

    @JsonProperty(value = "sport", required = true)
    private String sport;

    @JsonProperty(value = "ball", required = false)
    private Ball ball;

    @JsonProperty(value = "details", required = false)
    private Map<String, String> details;

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

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    @Override
    public String toString() {
        return "SportEvent{" +
                "sport='" + sport + '\'' +
                ", ball=" + ball +
                ", details=" + details +
                '}';
    }
}
