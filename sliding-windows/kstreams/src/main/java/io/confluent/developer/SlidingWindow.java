package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;


public class SlidingWindow {

    private static final Logger LOG = LoggerFactory.getLogger(SlidingWindow.class);
    public static final String INPUT_TOPIC = "temp-readings";
    public static final String OUTPUT_TOPIC = "output-topic";

    public Topology buildTopology(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, TemperatureReadingTimestampExtractor.class.getName());
        properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        final StreamsBuilder builder = new StreamsBuilder();

        Serde<TemperatureReading> tempReadingSerde = StreamsSerde.serdeFor(TemperatureReading.class);
        Serde<TempAverage> tempAverageSerde = StreamsSerde.serdeFor(TempAverage.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), tempReadingSerde))
                .groupByKey()
                .windowedBy(SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofMillis(500), Duration.ofMillis(100)))
                .aggregate(() -> new TempAverage(0, 0),
                        (key, value, agg) -> new TempAverage(agg.total() + value.temp(), agg.num_readings() + 1),
                        Materialized.with(Serdes.String(), tempAverageSerde))
                .toStream()
                .map((Windowed<String> key, TempAverage tempAverage) -> {
                    double aveNoFormat = tempAverage.total()/(double)tempAverage.num_readings();
                    double formattedAve = Double.parseDouble(String.format("%.2f", aveNoFormat));
                    return new KeyValue<>(key.key(),formattedAve) ;
                })
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));

        return builder.build(properties);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "sliding-window-application");

        SlidingWindow slidingWindow = new SlidingWindow();
        Topology topology = slidingWindow.buildTopology(properties);

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
}
