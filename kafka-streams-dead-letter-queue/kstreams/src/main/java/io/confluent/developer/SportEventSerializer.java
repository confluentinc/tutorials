package io.confluent.developer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom serializer for SportEvent to JSON.
 */
public class SportEventSerializer implements Serializer<SportEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SportEventSerializer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, SportEvent data) {
        if (data == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            LOG.error("Failed to serialize SportEvent to JSON", e);
            throw new SerializationException("Failed to serialize SportEvent to JSON", e);
        }
    }
}
