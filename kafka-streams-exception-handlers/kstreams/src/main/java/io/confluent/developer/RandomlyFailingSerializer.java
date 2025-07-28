package io.confluent.developer;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;


public class RandomlyFailingSerializer implements Serializer<String> {
    public byte[] serialize(String topic, String data) {
        if (Math.random() < 0.5) {
            throw new SerializationException("simulated SerializationException");
        }
        try {
            return data == null ? null : data.getBytes(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new SerializationException(e);
        }
    }
}
