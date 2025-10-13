package io.confluent.developer;

import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.KafkaShareConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsumerThread<Key, Value> implements Runnable, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerThread.class);

    private final String consumerId;
    private final KafkaShareConsumer<Key, Value> shareConsumer;
    private final KafkaConsumer<Key, Value> consumer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger eventCounter;
    private final int eventsToConsume;
    private final EventHandler<Key, Value> eventHandler;

    public ConsumerThread(String consumerId, KafkaShareConsumer<Key, Value> shareConsumer,
                          EventHandler<Key, Value> eventHandler,
                          AtomicInteger eventCounter,
                          int eventsToConsume,
                          String...topics) {
        this.consumerId = consumerId;
        this.shareConsumer = shareConsumer;
        this.consumer = null;
        this.shareConsumer.subscribe(Arrays.asList(topics));
        this.eventHandler = eventHandler;
        this.eventCounter = eventCounter;
        this.eventsToConsume = eventsToConsume;
    }

    public ConsumerThread(String consumerId, KafkaConsumer<Key, Value> consumer,
                          EventHandler<Key, Value> eventHandler,
                          AtomicInteger eventCounter,
                          int eventsToConsume,
                          String...topics) {
        this.consumerId = consumerId;
        this.consumer = consumer;
        this.shareConsumer = null;
        this.consumer.subscribe(Arrays.asList(topics));
        this.eventHandler = eventHandler;
        this.eventCounter = eventCounter;
        this.eventsToConsume = eventsToConsume;
    }

    @Override
    public void run() {
        try {
            while (!closed.get()) {
                if (eventCounter.get() >= eventsToConsume) {
                    break;
                }
                if (shareConsumer != null) {
                    ConsumerRecords<Key, Value> records = shareConsumer.poll(Duration.ofMillis(500));

                    for (ConsumerRecord<Key, Value> record : records) {
                        try {
                            if (eventCounter.get() >= eventsToConsume) {
                                break;
                            }
                            eventHandler.handleEvent(record.key(), record.value(), record.partition(), record.offset());
                            shareConsumer.acknowledge(record, AcknowledgeType.ACCEPT);
                        } catch (Exception e) {
                            LOGGER.error("consumer {}: error handling event: {}", consumerId, e.getMessage(), e);
                            shareConsumer.acknowledge(record, AcknowledgeType.REJECT);
                        }
                    }
                } else {
                    ConsumerRecords<Key, Value> records = consumer.poll(Duration.ofMillis(500));

                    for (ConsumerRecord<Key, Value> record : records) {
                        try {
                            if (eventCounter.get() >= eventsToConsume) {
                                break;
                            }
                            eventHandler.handleEvent(record.key(), record.value(), record.partition(), record.offset());
                        } catch (Exception e) {
                            LOGGER.error("consumer {}: error handling event: {}", consumerId, e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (WakeupException e) {
            // Ignore if closing
            if (!closed.get()) {
                throw e;
            }
        } finally {
            if (consumer != null) {
                consumer.close();
            } else {
                shareConsumer.close();
            }
            LOGGER.info("consumer {} closed", consumerId);
        }
    }


    public void shutdown() {
        closed.set(true);
        if (shareConsumer != null) {
            shareConsumer.wakeup();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public interface EventHandler<Key, Value> {
        void handleEvent(Key key, Value value, int partition, long offset);
    }
}
