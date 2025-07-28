package io.confluent.developer;

import org.apache.kafka.streams.errors.ErrorHandlerContext;
import org.apache.kafka.streams.errors.ProcessingExceptionHandler;
import org.apache.kafka.streams.processor.api.Record;

import java.util.Map;


public class ContinuingProcessingExceptionHandler implements ProcessingExceptionHandler {
    @Override
    public ProcessingHandlerResponse handle(final ErrorHandlerContext context,
                                            final Record<?, ?> record,
                                            final Exception exception) {
        System.out.println("ProcessingExceptionHandler triggered");
        return ProcessingHandlerResponse.CONTINUE;
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
