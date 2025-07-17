package io.confluent.developer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.errors.ErrorHandlerContext;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;

import java.util.Map;


public class ContinuingProductionExceptionHandler implements ProductionExceptionHandler {
    @Override
    public ProductionExceptionHandlerResponse handle(final ErrorHandlerContext context,
                                                     final ProducerRecord<byte[], byte[]> record,
                                                     final Exception exception) {
        System.out.println("ProductionExceptionHandler.handle triggered");
        return ProductionExceptionHandlerResponse.CONTINUE;
    }

    @Override
    public ProductionExceptionHandlerResponse handleSerializationException(final ErrorHandlerContext context,
                                                                           final ProducerRecord record,
                                                                           final Exception exception,
                                                                           final SerializationExceptionOrigin origin) {
        System.out.println("ProductionExceptionHandler.handleSerializationException triggered");
        return ProductionExceptionHandlerResponse.CONTINUE;
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
