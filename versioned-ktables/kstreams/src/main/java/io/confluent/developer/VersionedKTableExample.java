package io.confluent.developer;


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
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.VersionedBytesStoreSupplier;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class VersionedKTableExample {

    public static final String STREAM_INPUT_TOPIC = "stream-input-topic";
    public static final String TABLE_INPUT_TOPIC = "table-input-topic";
    public static final String OUTPUT_TOPIC = "output-topic";


    public Topology buildTopology() {
        final StreamsBuilder builder = new StreamsBuilder();

        final Serde<String> stringSerde = Serdes.String();

        final VersionedBytesStoreSupplier versionedStoreSupplier = Stores.persistentVersionedKeyValueStore("versioned-ktable-store", Duration.ofMinutes(10));

        final KStream<String, String> streamInput = builder.stream(STREAM_INPUT_TOPIC, Consumed.with(stringSerde, stringSerde));

        final KTable<String, String> tableInput = builder.table(TABLE_INPUT_TOPIC,
            Materialized.<String, String>as(versionedStoreSupplier)
                .withKeySerde(stringSerde)
                .withValueSerde(stringSerde));
        final ValueJoiner<String, String, String> valueJoiner = (val1, val2) -> val1 + " " + val2;

        streamInput.join(tableInput, valueJoiner)
            .peek((key, value) -> System.out.println("Joined value: " + value))
            .to(OUTPUT_TOPIC,
                Produced.with(stringSerde, stringSerde));

        return builder.build();
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "versioned-ktable-application");

        TutorialDataGenerator dataGenerator = new TutorialDataGenerator(properties);
        dataGenerator.generate();

        VersionedKTableExample versionedKTable = new VersionedKTableExample();
        Topology topology = versionedKTable.buildTopology();

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only; don't do this in production as it wipes out all local state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
                final String topic1 = STREAM_INPUT_TOPIC;
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
                final String topic2 = TABLE_INPUT_TOPIC;
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
