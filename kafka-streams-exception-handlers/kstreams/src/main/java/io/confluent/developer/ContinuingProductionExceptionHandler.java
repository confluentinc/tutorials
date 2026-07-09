package io.confluent.developer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.errors.ErrorHandlerContext;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;

import java.util.Map;


public class ContinuingProductionExceptionHandler implements ProductionExceptionHandler {
    @Override
    public Response handleError(final ErrorHandlerContext context,
                                 final ProducerRecord<byte[], byte[]> record,
                                 final Exception exception) {
        System.out.println("ProductionExceptionHandler.handleError triggered");
        return Response.resume();
    }

    @Override
    public Response handleSerializationError(final ErrorHandlerContext context,
                                              final ProducerRecord record,
                                              final Exception exception,
                                              final SerializationExceptionOrigin origin) {
        System.out.println("ProductionExceptionHandler.handleSerializationError triggered");
        return Response.resume();
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
