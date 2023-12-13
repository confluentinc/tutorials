package io.confluent.developer;

import io.confluent.developer.avro.Clicks;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SessionWindows;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

public class SessionWindow {

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.LONG)
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());

    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = allProps.getProperty("input.topic.name");
        final String outputTopic = allProps.getProperty("output.topic.name");
        final SpecificAvroSerde<Clicks> clicksSerde = getSpecificAvroSerde(allProps);

        builder.stream(inputTopic, Consumed.with(Serdes.String(), clicksSerde))
                .groupByKey()
                .windowedBy(SessionWindows.ofInactivityGapAndGrace(Duration.ofMinutes(5), Duration.ofSeconds(30)))
                .count()
                .toStream()
                .map((windowedKey, count) ->  {
                    String start = timeFormatter.format(windowedKey.window().startTime());
                    String end = timeFormatter.format(windowedKey.window().endTime());
                    String sessionInfo = String.format("Session info started: %s ended: %s with count %s", start, end, count);
                    return KeyValue.pair(windowedKey.key(), sessionInfo);
                })
                .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));

        return builder.build();
    }


    static <T extends SpecificRecord> SpecificAvroSerde<T> getSpecificAvroSerde(final Properties allProps) {
        final SpecificAvroSerde<T> specificAvroSerde = new SpecificAvroSerde<>();
        final Map<String, String> serdeConfig = (Map)allProps;
        specificAvroSerde.configure(serdeConfig, false);
        return specificAvroSerde;
    }
    public void createTopics(Properties allProps) {
        try (AdminClient client = AdminClient.create(allProps)) {
            List<NewTopic> topicList = new ArrayList<>();

            NewTopic sessionInput = new NewTopic(allProps.getProperty("input.topic.name"),
                    Integer.parseInt(allProps.getProperty("input.topic.partitions")),
                    Short.parseShort(allProps.getProperty("input.topic.replication.factor")));
            topicList.add(sessionInput);

            NewTopic counts = new NewTopic(allProps.getProperty("output.topic.name"),
                    Integer.parseInt(allProps.getProperty("output.topic.partitions")),
                    Short.parseShort(allProps.getProperty("output.topic.replication.factor")));

            topicList.add(counts);
            client.createTopics(topicList);
        }
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties allProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        allProps.load(input);
        input.close();

        return allProps;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        SessionWindow tw = new SessionWindow();
        Properties allProps = tw.loadEnvProperties(args[0]);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        allProps.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, ClickEventTimestampExtractor.class);
        Topology topology = tw.buildTopology(allProps);

        tw.createTopics(allProps);
        ClicksDataGenerator dataGenerator = new ClicksDataGenerator(allProps);
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
            streams.cleanUp();
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

    static class ClicksDataGenerator {
        final Properties properties;


        public ClicksDataGenerator(final Properties properties) {
            this.properties = properties;
        }

        public void generate() {
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

            try (Producer<String, Clicks> producer = new KafkaProducer<>(properties)) {
                String topic = properties.getProperty("input.topic.name");
                List<Clicks> sessionClicks = new ArrayList<>();
                final String keyOne = "51.56.119.117";
                final String keyTwo = "53.170.33.192";

                Instant instant = Instant.now();
                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("/etiam/justo/etiam/pretium/iaculis.xml").setTimestamp(instant.toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("vestibulum/vestibulum/ante/ipsum/primis/in.json").setTimestamp(instant.plusMillis(9000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("/mauris/morbi/non.jpg").setTimestamp(instant.plusMillis(24000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("/nullam/orci/pede/venenatis.json").setTimestamp(instant.plusMillis(38000).toEpochMilli()).build());

                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/etiam/justo/etiam/pretium/iaculis.xml").setTimestamp(instant.plusMillis(10000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/mauris/morbi/non.jpg").setTimestamp(instant.plusMillis(32000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/nec/euismod/scelerisque/quam.xml").setTimestamp(instant.plusMillis(44000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/nullam/orci/pede/venenatis.json").setTimestamp(instant.plusMillis(58000).toEpochMilli()).build());

                Instant newSessionInstant = instant.plus(2, ChronoUnit.HOURS);

                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("/etiam/justo/etiam/pretium/iaculis.xml").setTimestamp(newSessionInstant.toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("vestibulum/vestibulum/ante/ipsum/primis/in.json").setTimestamp(newSessionInstant.plusMillis(2000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("/mauris/morbi/non.jpg").setTimestamp(newSessionInstant.plusMillis(4000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyOne).setUrl("/nullam/orci/pede/venenatis.json").setTimestamp(newSessionInstant.plusMillis(10000).toEpochMilli()).build());

                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/etiam/justo/etiam/pretium/iaculis.xml").setTimestamp(newSessionInstant.plusMillis(11000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/mauris/morbi/non.jpg").setTimestamp(newSessionInstant.plusMillis(12000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/nec/euismod/scelerisque/quam.xml").setTimestamp(newSessionInstant.plusMillis(14000).toEpochMilli()).build());
                sessionClicks.add(Clicks.newBuilder().setIp(keyTwo).setUrl("/nullam/orci/pede/venenatis.json").setTimestamp(newSessionInstant.plusMillis(28000).toEpochMilli()).build());

                sessionClicks.forEach(click -> {
                    producer.send(new ProducerRecord<>(topic, click.getIp(), click), (metadata, exception) -> {
                            if (exception != null) {
                                exception.printStackTrace(System.out);
                            } else {
                                System.out.printf("Produced record at offset %d to topic %s \n", metadata.offset(), metadata.topic());
                            }
                    });
                });
            }
        }
    }
}
