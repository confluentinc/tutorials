package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import static org.apache.kafka.streams.kstream.Suppressed.BufferConfig.unbounded;

public class WindowFinalResult {

    private static final Logger LOG = LoggerFactory.getLogger(WindowFinalResult.class);
    public static final String INPUT_TOPIC = "final-result-input";
    public static final String OUTPUT_TOPIC = "final-result-output";

    public Topology buildTopology(Properties properties) {
        StreamsBuilder builder = new StreamsBuilder();

        Serde<PressureAlert> pressureSerde = StreamsSerde.serdeFor(PressureAlert.class);

        Produced<Windowed<String>, Long> producedCount = Produced
            .with(new WindowedSerdes.TimeWindowedSerde<>(Serdes.String(), Long.MAX_VALUE), Serdes.Long());

        Consumed<String, PressureAlert> consumedPressure = Consumed
            .with(Serdes.String(), pressureSerde)
            .withTimestampExtractor(new PressureDatetimeExtractor());

        TimeWindows windows = TimeWindows
            .ofSizeAndGrace(Duration.ofSeconds(10), Duration.ofSeconds(20))
            .advanceBy(Duration.ofSeconds(10));

        builder.stream(INPUT_TOPIC, consumedPressure)
            // peek statement added for visibility to Kafka Streams record processing NOT required!
            .peek((key, value) -> LOG.info("Incoming record value[{}]", value))
            .selectKey((key, value) -> value.id())
            .groupByKey(Grouped.with(Serdes.String(), pressureSerde))
            .windowedBy(windows)
            .count()
            .suppress(Suppressed.untilWindowCloses(unbounded()))
            .toStream()
            // peek statement added for visibility to Kafka Streams record processing NOT required!
            .peek((key, value) -> LOG.info(String.format("Outgoing count key:[%s] value:[%s]", key, value)))
            .to(OUTPUT_TOPIC, producedCount);

        return builder.build(properties);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "window-final-result-application");

        WindowFinalResult runningAverage = new WindowFinalResult();
        Topology topology = runningAverage.buildTopology(properties);

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
