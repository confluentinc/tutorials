package io.confluent.developer;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;


/**
 * Processor that randomly (50% of the time) throws an exception, and otherwise forwards the message along as-is.
 */
public class RandomlyFailingProcessorSupplier implements ProcessorSupplier<Integer, String, Integer, String> {

    @Override
    public Processor<Integer, String, Integer, String> get() {
        return new Processor() {
            private ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
                this.context = context;
            }
            @Override
            public void process(Record record) {
                if (Math.random() < 0.5) {
                    throw new RuntimeException("fail!!");
                }
                context.forward(record);
            }
        };
    }

}
