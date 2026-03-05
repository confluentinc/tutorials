package io.confluent.developer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Dimensions {

    @JsonProperty("diameter")
    private String diameter;

    @JsonProperty("weight")
    private String weight;

    public Dimensions() {
    }

    public Dimensions(String diameter, String weight) {
        this.diameter = diameter;
        this.weight = weight;
    }

    public String getDiameter() {
        return diameter;
    }

    public void setDiameter(String diameter) {
        this.diameter = diameter;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    @Override
    public String toString() {
        return "Dimensions{" +
                "diameter='" + diameter + '\'' +
                ", weight='" + weight + '\'' +
                '}';
    }
}
