package io.confluent.developer;


import org.apache.kafka.clients.consumer.ConsumerRecord;


/**
 * Record handler that sleeps a given number of milliseconds.
 */
public class SleepingRecordHandler extends ConsumerRecordHandler<String, String> {

  private final int numMilliseconds;

  public SleepingRecordHandler(final int numMilliseconds) {
    this.numMilliseconds = numMilliseconds;
  }

  @Override
  protected void processRecordImpl(final ConsumerRecord consumerRecord) {
    try {
      Thread.sleep(numMilliseconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
