package io.confluent.developer;


import io.confluent.developer.avro.LoginTime;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsPunctuation {


	public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));

        return props;
    }

    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String loginTimeInputTopic = envProps.getProperty("input.topic.name");
        final String outputTopic = envProps.getProperty("output.topic.name");
        final String loginTimeStore = "logintime-store";
        final Serde<LoginTime> loginTimeSerde = getSpecificAvroSerde(envProps);
        StoreBuilder<KeyValueStore<String, Long>> storeBuilder = Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(loginTimeStore),Serdes.String(), Serdes.Long());
        builder.addStateStore(storeBuilder);
        final KStream<String, LoginTime> loginTimeStream = builder.stream(loginTimeInputTopic, Consumed.with(Serdes.String(), loginTimeSerde));

        loginTimeStream.transform(getTransformerSupplier(loginTimeStore), Named.as("max-login-time-transformer"),loginTimeStore)
                      .to(outputTopic, Produced.with(Serdes.String(), Serdes.Long()));

        return builder.build();
    }


    private TransformerSupplier<String, LoginTime, KeyValue<String, Long>> getTransformerSupplier(final String storeName) {
	    return () -> new Transformer<String, LoginTime, KeyValue<String, Long>>() {
	        private KeyValueStore<String, Long> store;
	        private ProcessorContext context;
            @Override
            public void init(ProcessorContext context) {
                   this.context = context;
                   store = (KeyValueStore<String, Long>) this.context.getStateStore(storeName);
                   this.context.schedule(Duration.ofSeconds(5), PunctuationType.STREAM_TIME, this::streamTimePunctuator);
                   this.context.schedule(Duration.ofSeconds(20), PunctuationType.WALL_CLOCK_TIME, this::wallClockTimePunctuator);
            }

            void wallClockTimePunctuator(Long timestamp){
                try (KeyValueIterator<String, Long> iterator = store.all()) {
                    while (iterator.hasNext()) {
                        KeyValue<String, Long> keyValue = iterator.next();
                        store.put(keyValue.key, 0L);
                    }
                }
                System.out.println("@" + new Date(timestamp) +" Reset all view-times to zero");
            }

            void streamTimePunctuator(Long timestamp) {
                Long maxValue = Long.MIN_VALUE;
                String maxValueKey = "";
                try (KeyValueIterator<String, Long> iterator = store.all()) {
                    while (iterator.hasNext()) {
                        KeyValue<String, Long> keyValue = iterator.next();
                        if (keyValue.value > maxValue) {
                            maxValue = keyValue.value;
                            maxValueKey = keyValue.key;
                        }
                    }
                }
                context.forward(maxValueKey +" @" + new Date(timestamp), maxValue);
            }

            @Override
            public KeyValue<String, Long> transform(String key, LoginTime value) {
                   Long currentVT = store.putIfAbsent(key, value.getLogintime());
                   if (currentVT != null) {
                       store.put(key, currentVT + value.getLogintime());
                   }
                   return null;
            }

            @Override
            public void close() {

            }
        };
    }



    static <T extends SpecificRecord> SpecificAvroSerde<T> getSpecificAvroSerde(final Properties envProps) {
        final SpecificAvroSerde<T> specificAvroSerde = new SpecificAvroSerde<>();

        final HashMap<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                envProps.getProperty("schema.registry.url"));

        specificAvroSerde.configure(serdeConfig, false);
        return specificAvroSerde;
    }

    public void createTopics(final Properties envProps) {
        final Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", envProps.getProperty("bootstrap.servers"));
        try (final AdminClient client = AdminClient.create(config)) {

        final List<NewTopic> topics = new ArrayList<>();
        
            topics.add(new NewTopic(
                    envProps.getProperty("output.topic.name"),
                    Integer.parseInt(envProps.getProperty("output.topic.partitions")),
                    Short.parseShort(envProps.getProperty("output.topic.replication.factor"))));

            client.createTopics(topics);
        }
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        final Properties envProps = new Properties();
        final FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        final KafkaStreamsPunctuation instance = new KafkaStreamsPunctuation();
        final Properties envProps = instance.loadEnvProperties(args[0]);
        final Properties streamProps = instance.buildStreamsProperties(envProps);
        final Topology topology = instance.buildTopology(envProps);

        instance.createTopics(envProps);

        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close(Duration.ofSeconds(5));
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

}
