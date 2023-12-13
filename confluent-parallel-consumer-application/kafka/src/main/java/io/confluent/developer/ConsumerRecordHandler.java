package io.confluent.developer;


import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Abstract class for processing a record. This allows for easier experimentation with the application and perf tests
 * in this package. It handles tracking the number of events so that apps can wait on consuming an expected number
 * of events.
 */
public abstract class ConsumerRecordHandler<K, V> {

  private final AtomicInteger numRecordsProcessed;

  public ConsumerRecordHandler() {
    numRecordsProcessed = new AtomicInteger(0);
  }

  protected abstract void processRecordImpl(ConsumerRecord<K, V> consumerRecord);

  final void processRecord(final ConsumerRecord<K, V> consumerRecord) {
    processRecordImpl(consumerRecord);
    numRecordsProcessed.incrementAndGet();
  }

  final int getNumRecordsProcessed() {
    return numRecordsProcessed.get();
  }

}
