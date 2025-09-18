package io.confluent.developer;


import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsPunctuation {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsPunctuation.class);
    private static final String LOGIN_TIME_STORE = "logintime-store";
    public static final String INPUT_TOPIC = "punctuation-input";
    public static final String OUTPUT_TOPIC = "punctuation-output";

    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final Serde<LoginTime> loginTimeSerde = StreamsSerde.serdeFor(LoginTime.class);

        final KStream<String, LoginTime> loginTimeStream = builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), loginTimeSerde));

        loginTimeStream
                .peek((key, value) -> LOG.info("Incoming records key[{}] value[{}]", key, value))
                .process(new PunctationProcessorSupplier(), Named.as("max-login-time-transformer"), LOGIN_TIME_STORE)
                .peek((key, value) -> LOG.info("Punctuation records key[{}] value[{}]", key, value))
                      .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Integer()));

        return builder.build(envProps);
    }


    private static class PunctationProcessorSupplier implements ProcessorSupplier<String, LoginTime, String, Integer> {

        @Override
        public Set<StoreBuilder<?>> stores() {
            StoreBuilder<KeyValueStore<String, Integer>> storeBuilder =
                    Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(LOGIN_TIME_STORE),Serdes.String(), Serdes.Integer());
            return Collections.singleton(storeBuilder);
        }

        @Override
        public Processor<String, LoginTime, String, Integer> get() {
            return new Processor<String, LoginTime, String, Integer>() {
                private KeyValueStore<String, Integer> store;
                private ProcessorContext<String, Integer> context;

                @Override
                public void init(ProcessorContext<String, Integer> context) {
                    this.context = context;
                    store = this.context.getStateStore(LOGIN_TIME_STORE);
                    this.context.schedule(Duration.ofSeconds(5), PunctuationType.STREAM_TIME, this::streamTimePunctuator);
                    this.context.schedule(Duration.ofSeconds(20), PunctuationType.WALL_CLOCK_TIME, this::wallClockTimePunctuator);
                }

                @Override
                public void process(Record<String, LoginTime> loginRecord) {
                    String key = loginRecord.key();
                    LoginTime value = loginRecord.value();
                    Integer currentVT = store.putIfAbsent(key, value.logInTime());
                    if (currentVT != null) {
                        store.put(key, currentVT + value.logInTime());
                    }
                }

                void wallClockTimePunctuator(Long timestamp) {
                    try (KeyValueIterator<String, Integer> iterator = store.all()) {
                        while (iterator.hasNext()) {
                            KeyValue<String, Integer> keyValue = iterator.next();
                            store.put(keyValue.key, 0);
                        }
                    }
                    System.out.println("@" + new Date(timestamp) + " Reset all view-times to zero");
                }

                void streamTimePunctuator(Long timestamp) {
                    Integer maxValue = Integer.MIN_VALUE;
                    String maxValueKey = "";
                    try (KeyValueIterator<String, Integer> iterator = store.all()) {
                        while (iterator.hasNext()) {
                            KeyValue<String, Integer> keyValue = iterator.next();
                            if (keyValue.value > maxValue) {
                                maxValue = keyValue.value;
                                maxValueKey = keyValue.key;
                            }
                        }
                    }
                    context.forward(new Record<>(maxValueKey + " @" + new Date(timestamp), maxValue, timestamp));
                }
            };
        }
    }


    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-punctuation");
        KafkaStreamsPunctuation kafkaStreamsPunctuation = new KafkaStreamsPunctuation();

        Topology topology = kafkaStreamsPunctuation.buildTopology(properties);

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only don't do this in production as it wipes out all local state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
