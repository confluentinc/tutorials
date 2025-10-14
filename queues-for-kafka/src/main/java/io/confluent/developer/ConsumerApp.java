package io.confluent.developer;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.KafkaShareConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.confluent.developer.ConsumerAppArgParser.streamFromFile;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

public class ConsumerApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerApp.class);

    private static Properties getKafkaProperties(String kafkaProperties, ConsumerAppArgParser.ConsumerType consumerType) {
        Properties properties = new Properties();
        try (InputStream inputStream = streamFromFile(kafkaProperties)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        properties.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
        properties.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
        if (consumerType.equals(ConsumerAppArgParser.ConsumerType.SHARE_CONSUMER)) {
            properties.put(SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");
            properties.put(MAX_POLL_RECORDS_CONFIG, "100");
            properties.put(GROUP_ID_CONFIG, "share-consumer-group");
        } else {
            properties.put(AUTO_OFFSET_RESET_CONFIG, "earliest"); // only applies to KafkaConsumer
            properties.put(GROUP_ID_CONFIG, "consumer-group");
        }

        return properties;
    }


    public static void main(final String[] args) {
        ConsumerAppArgParser cmdArgs = ConsumerAppArgParser.parseOptions(args);
        final String topic = "strings";

        final ConsumerAppArgParser.ConsumerType consumerType = cmdArgs.getConsumerType();
        final int numConsumerThreads = cmdArgs.getNumConsumers();
        final int processingTimeMs = cmdArgs.getSleepMs();
        final int eventsToConsume = cmdArgs.getTotalEvents();

        AtomicBoolean printedCompletion = new AtomicBoolean(false);
        AtomicInteger eventCounter = new AtomicInteger(0);

        LOGGER.info("starting {} consumers", numConsumerThreads);

        ExecutorService executorService = newFixedThreadPool(1 + numConsumerThreads);
        List<ConsumerThread<String, String>> consumerThreads = new ArrayList<>();
        List<Future> consumerThreadFutures = new ArrayList<>();
        long startTime = System.nanoTime();
        long eventsPerSec = Math.round(1000.0 / processingTimeMs * numConsumerThreads);

        for (int i = 0; i < numConsumerThreads; i++) {
            final String consumerId = "consumer-" + i;

            ConsumerThread<String, String> consumer;
            ConsumerThread.EventHandler eventHandler = (ConsumerThread.EventHandler<String, String>) (key, value, partition, offset) -> {
                try {
                    Thread.sleep(processingTimeMs);
                    int currentCount = eventCounter.incrementAndGet();

                    // log progress about once every 5 seconds
                    if (currentCount % (5 * eventsPerSec) == 0) {
                        LOGGER.info("Consumed {} of {} events so far", currentCount, eventsToConsume);
                    }

                    if (currentCount >= eventsToConsume) {
                        long endTime = System.nanoTime();
                        double elapsedTimeSeconds = (double) (endTime - startTime) / 1_000_000_000;
                        if (printedCompletion.getAndSet(true) == false) {
                            LOGGER.info("Completed consuming {} messages in {} seconds.", eventsToConsume, elapsedTimeSeconds);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            if (consumerType.equals(ConsumerAppArgParser.ConsumerType.SHARE_CONSUMER)) {
                KafkaShareConsumer shareConsumer = new KafkaShareConsumer<>(getKafkaProperties(cmdArgs.getKafkaProperties(), consumerType));
                consumer = new ConsumerThread<>(
                        consumerId,
                        shareConsumer,
                        eventHandler,
                        eventCounter,
                        eventsToConsume,
                        topic);
                consumerThreads.add(consumer);
                consumerThreadFutures.add(executorService.submit(consumer));
            } else {
                KafkaConsumer kafkaConsumer = new KafkaConsumer<>(getKafkaProperties(cmdArgs.getKafkaProperties(), consumerType));
                consumer = new ConsumerThread<>(
                        consumerId,
                        kafkaConsumer,
                        eventHandler,
                        eventCounter,
                        eventsToConsume,
                        topic);
                consumerThreads.add(consumer);
                consumerThreadFutures.add(executorService.submit(consumer));
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown signal received");

            for (ConsumerThread<String, String> consumerThread : consumerThreads) {
                consumerThread.shutdown();
            }

            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));

        for (Future consumerThreadFuture : consumerThreadFutures) {
            try {
                consumerThreadFuture.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        LOGGER.info("Consumers completed");
        System.exit(0);
    }

}
