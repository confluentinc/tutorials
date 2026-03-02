package io.confluent.developer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Ball {

    @JsonProperty("shape")
    private String shape;

    @JsonProperty("dimensions")
    private Dimensions dimensions;

    public Ball() {
    }

    public Ball(String shape, Dimensions dimensions) {
        this.shape = shape;
        this.dimensions = dimensions;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public void setDimensions(Dimensions dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public String toString() {
        return "Ball{" +
                "shape='" + shape + '\'' +
                ", dimensions=" + dimensions +
                '}';
    }
}
