package io.confluent.developer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Custom deserializer for SportEvent that validates required fields during deserialization.
 * This ensures that validation failures are caught by KIP-1034 DLQ mechanism.
 */
public class SportEventDeserializer implements Deserializer<SportEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SportEventDeserializer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SportEvent deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            SportEvent event = objectMapper.readValue(data, SportEvent.class);

            // Validate that ball field is present
            if (event.getBall().isEmpty()) {
                String errorMsg = String.format("Sport '%s' is missing required 'ball' field", event.getSport());
                LOG.error(errorMsg);
                throw new SerializationException(errorMsg);
            }

            LOG.debug("Successfully deserialized SportEvent: {}", event.getSport());
            return event;
        } catch (IOException e) {
            LOG.error("Failed to deserialize JSON to SportEvent", e);
            throw new SerializationException("Failed to deserialize JSON to SportEvent", e);
        }
    }
}
