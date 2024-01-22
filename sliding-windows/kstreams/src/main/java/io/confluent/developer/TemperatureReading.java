package io.confluent.developer;

public record TemperatureReading(double temp, long timestamp, String device_id) { }
