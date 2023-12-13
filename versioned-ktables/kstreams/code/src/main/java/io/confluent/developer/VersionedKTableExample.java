package io.confluent.developer;


import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.VersionedBytesStoreSupplier;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class VersionedKTableExample {


    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String streamInputTopic = allProps.getProperty("stream.topic.name");
        final String tableInputTopic = allProps.getProperty("table.topic.name");
        final String totalResultOutputTopic = allProps.getProperty("output.topic.name");

        final Serde<String> stringSerde = Serdes.String();

        final VersionedBytesStoreSupplier versionedStoreSupplier = Stores.persistentVersionedKeyValueStore("versioned-ktable-store", Duration.ofMinutes(10));
        final KeyValueBytesStoreSupplier persistentStoreSupplier = Stores.persistentKeyValueStore("non-versioned-table");

        final KStream<String, String> streamInput = builder.stream(streamInputTopic, Consumed.with(stringSerde, stringSerde));

        final KTable<String, String> tableInput = builder.table(tableInputTopic,
                Materialized.<String, String>as(versionedStoreSupplier)
                        .withKeySerde(stringSerde)
                        .withValueSerde(stringSerde));
        final ValueJoiner<String, String, String> valueJoiner = (val1, val2) -> val1 + " " + val2;

        streamInput.join(tableInput, valueJoiner)
                .peek((key, value) -> System.out.println("Joined value: " + value))
                .to(totalResultOutputTopic,
                        Produced.with(stringSerde, stringSerde));

        return builder.build();
    }

    public void createTopics(final Properties allProps) {
        try (final AdminClient client = AdminClient.create(allProps)) {

            final List<NewTopic> topics = new ArrayList<>();

            topics.add(new NewTopic(
                    allProps.getProperty("stream.topic.name"),
                    Integer.parseInt(allProps.getProperty("stream.topic.partitions")),
                    Short.parseShort(allProps.getProperty("stream.topic.replication.factor"))));

            topics.add(new NewTopic(
                    allProps.getProperty("table.topic.name"),
                    Integer.parseInt(allProps.getProperty("table.topic.partitions")),
                    Short.parseShort(allProps.getProperty("table.topic.replication.factor"))));

            topics.add(new NewTopic(
                    allProps.getProperty("output.topic.name"),
                    Integer.parseInt(allProps.getProperty("output.topic.partitions")),
                    Short.parseShort(allProps.getProperty("output.topic.replication.factor"))));

            client.createTopics(topics);
        }
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        final Properties allProps = new Properties();
        try (final FileInputStream input = new FileInputStream(fileName)) {
            allProps.load(input);
        }
        return allProps;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        final VersionedKTableExample instance = new VersionedKTableExample();
        final Properties allProps = instance.loadEnvProperties(args[0]);
        final Topology topology = instance.buildTopology(allProps);

        instance.createTopics(allProps);

        TutorialDataGenerator dataGenerator = new TutorialDataGenerator(allProps);
        dataGenerator.generate();

        final KafkaStreams streams = new KafkaStreams(topology, allProps);
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

    record TutorialDataGenerator(Properties properties) {

        public void generate() {
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

            try (Producer<String, String> producer = new KafkaProducer<>(properties)) {
                HashMap<String, List<KeyValue<String, String>>> entryData = new HashMap<>();
                HashMap<String, List<Long>> dataTimestamps = new HashMap<>();
                Instant now = Instant.now();

                List<KeyValue<String, String>> streamMessagesOutOfOrder = Arrays.asList(
                        KeyValue.pair("one", "peanut butter and"),
                        KeyValue.pair("two", "ham and"),
                        KeyValue.pair("three", "cheese and"),
                        KeyValue.pair("four", "tea and"),
                        KeyValue.pair("five", "coffee with")
                );
                final String topic1 = properties.getProperty("stream.topic.name");
                entryData.put(topic1, streamMessagesOutOfOrder);

                List<Long> timestamps = Arrays.asList(
                        now.minus(50, ChronoUnit.SECONDS).toEpochMilli(),
                        now.minus(40, ChronoUnit.SECONDS).toEpochMilli(),
                        now.minus(30, ChronoUnit.SECONDS).toEpochMilli(),
                        now.minus(20, ChronoUnit.SECONDS).toEpochMilli(),
                        now.minus(10, ChronoUnit.SECONDS).toEpochMilli()
                );
                dataTimestamps.put(topic1, timestamps);

                List<KeyValue<String, String>> tableMessagesOriginal = Arrays.asList(
                        KeyValue.pair("one", "jelly"),
                        KeyValue.pair("two", "eggs"),
                        KeyValue.pair("three", "crackers"),
                        KeyValue.pair("four", "crumpets"),
                        KeyValue.pair("five", "cream"));
                final String topic2 = properties.getProperty("table.topic.name");
                entryData.put(topic2, tableMessagesOriginal);
                dataTimestamps.put(topic2, timestamps);


                produceRecords(entryData, producer, dataTimestamps);
                entryData.clear();
                dataTimestamps.clear();

                List<KeyValue<String, String>> tableMessagesLater = Arrays.asList(
                        KeyValue.pair("one", "sardines"),
                        KeyValue.pair("two", "an old tire"),
                        KeyValue.pair("three", "fish eyes"),
                        KeyValue.pair("four", "moldy bread"),
                        KeyValue.pair("five", "lots of salt"));
                entryData.put(topic2, tableMessagesLater);

                List<Long> forwardTimestamps = Arrays.asList(
                        now.plus(50, ChronoUnit.SECONDS).toEpochMilli(),
                        now.plus(40, ChronoUnit.SECONDS).toEpochMilli(),
                        now.plus(30, ChronoUnit.SECONDS).toEpochMilli(),
                        now.plus(30, ChronoUnit.SECONDS).toEpochMilli(),
                        now.plus(30, ChronoUnit.SECONDS).toEpochMilli()
                );
                dataTimestamps.put(topic2, forwardTimestamps);

                produceRecords(entryData, producer, dataTimestamps);

            }
        }

        private static void produceRecords(HashMap<String, List<KeyValue<String, String>>> entryData,
                                           Producer<String, String> producer,
                                           HashMap<String, List<Long>> timestampsMap) {
            entryData.forEach((topic, list) ->
                    {
                        List<Long> timestamps = timestampsMap.get(topic);
                        for (int i = 0; i < list.size(); i++) {
                            long timestamp = timestamps.get(i);
                            String key = list.get(i).key;
                            String value = list.get(i).value;
                            producer.send(new ProducerRecord<>(topic, 0, timestamp, key, value), (metadata, exception) -> {
                                if (exception != null) {
                                    exception.printStackTrace(System.out);
                                } else {
                                    System.out.printf("Produced record at offset %d to topic %s %n", metadata.offset(), metadata.topic());
                                }
                            });
                        }
                    }
            );
        }
    }

}
