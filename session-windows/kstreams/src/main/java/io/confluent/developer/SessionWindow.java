package io.confluent.developer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.kafka.serializers.KafkaAvroSerializer;

public class SessionWindow {

    private static final Logger LOG = LoggerFactory.getLogger(SessionWindow.class);
    public static final String INPUT_TOPIC = "clicks";
    public static final String OUTPUT_TOPIC = "sessions";

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.LONG)
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());

    public Topology buildTopology(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, ClickEventTimestampExtractor.class.getName());
        properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        final StreamsBuilder builder = new StreamsBuilder();

        Serde<Click> clickSerde = StreamsSerde.serdeFor(Click.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), clickSerde))
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
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        return builder.build(properties);
    }


    public static void main(String[] args) {

        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "session-window-application");

        ClicksDataGenerator generator = new ClicksDataGenerator(properties);
        generator.generate();

        SessionWindow sessionWindow = new SessionWindow();
        Topology topology = sessionWindow.buildTopology(properties);

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

    static class ClicksDataGenerator {
        final Properties properties;


        public ClicksDataGenerator(final Properties properties) {
            this.properties = properties;
        }

        public void generate() {
            Serde<Click> clickSerde = StreamsSerde.serdeFor(Click.class);
                    
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, clickSerde.serializer());

            try (Producer<String, Click> producer = new KafkaProducer<>(properties)) {
                String topic = properties.getProperty("input.topic.name");
                List<Click> sessionClicks = new ArrayList<>();
                final String keyOne = "51.56.119.117";
                final String keyTwo = "53.170.33.192";

                Instant instant = Instant.now();
                sessionClicks.add(new Click(keyOne, instant.toEpochMilli(), "/etiam/justo/etiam/pretium/iaculis.xml"));
                sessionClicks.add(new Click(keyOne, instant.plusMillis(9000).toEpochMilli(), "vestibulum/vestibulum/ante/ipsum/primis/in.json"));
                sessionClicks.add(new Click(keyOne, instant.plusMillis(24000).toEpochMilli(), "/mauris/morbi/non.jpg"));
                sessionClicks.add(new Click(keyOne, instant.plusMillis(38000).toEpochMilli(), "/nullam/orci/pede/venenatis.json"));

                sessionClicks.add(new Click(keyTwo, instant.plusMillis(10000).toEpochMilli(), "/etiam/justo/etiam/pretium/iaculis.xml"));
                sessionClicks.add(new Click(keyTwo, instant.plusMillis(32000).toEpochMilli(), "/mauris/morbi/non.jpg"));
                sessionClicks.add(new Click(keyTwo, instant.plusMillis(44000).toEpochMilli(), "/nec/euismod/scelerisque/quam.xml"));
                sessionClicks.add(new Click(keyTwo, instant.plusMillis(58000).toEpochMilli(), "/nullam/orci/pede/venenatis.json"));

                Instant newSessionInstant = instant.plus(2, ChronoUnit.HOURS);

                sessionClicks.add(new Click(keyOne, newSessionInstant.toEpochMilli(), "/etiam/justo/etiam/pretium/iaculis.xml"));
                sessionClicks.add(new Click(keyOne, newSessionInstant.plusMillis(2000).toEpochMilli(), "vestibulum/vestibulum/ante/ipsum/primis/in.json"));
                sessionClicks.add(new Click(keyOne, newSessionInstant.plusMillis(4000).toEpochMilli(), "/mauris/morbi/non.jpg"));
                sessionClicks.add(new Click(keyOne, newSessionInstant.plusMillis(10000).toEpochMilli(), "/nullam/orci/pede/venenatis.json"));

                sessionClicks.add(new Click(keyTwo, newSessionInstant.plusMillis(11000).toEpochMilli(), "/etiam/justo/etiam/pretium/iaculis.xml"));
                sessionClicks.add(new Click(keyTwo, newSessionInstant.plusMillis(12000).toEpochMilli(), "/mauris/morbi/non.jpg"));
                sessionClicks.add(new Click(keyTwo, newSessionInstant.plusMillis(14000).toEpochMilli(), "/nec/euismod/scelerisque/quam.xml"));
                sessionClicks.add(new Click(keyTwo, newSessionInstant.plusMillis(28000).toEpochMilli(), "/nullam/orci/pede/venenatis.json"));

                sessionClicks.forEach(click -> {
                    producer.send(new ProducerRecord<>(topic, click.ip(), click), (metadata, exception) -> {
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
