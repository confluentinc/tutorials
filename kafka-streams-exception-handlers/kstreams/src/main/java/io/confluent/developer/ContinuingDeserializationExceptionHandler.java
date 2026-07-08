package io.confluent.developer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.ErrorHandlerContext;

import java.util.Map;


public class ContinuingDeserializationExceptionHandler implements DeserializationExceptionHandler {
    @Override
    public Response handleError(final ErrorHandlerContext context,
                                 final ConsumerRecord<byte[], byte[]> record,
                                 final Exception exception) {
        System.out.println("DeserializationExceptionHandler triggered");
        return Response.resume();
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
