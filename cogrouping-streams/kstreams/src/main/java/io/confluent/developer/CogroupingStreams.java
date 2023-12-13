package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class CogroupingStreams {
     private static final Logger LOG = LoggerFactory.getLogger(CogroupingStreams.class);
     public static final String APP_ONE_INPUT_TOPIC = "app-one-input";
     public static final String APP_TWO_INPUT_TOPIC = "app-two-input";
     public static final String APP_THREE_INPUT_TOPIC = "app-three-input";
     public  static final String OUTPUT_TOPIC = "cogrouping-output";

     private final Serde<String> stringSerde = Serdes.String();
     private final Serde<LoginEvent> loginEventSerde = StreamsSerde.serdeFor(LoginEvent.class);
     private final Serde<LoginRollup> loginRollupSerde = StreamsSerde.serdeFor(LoginRollup.class);
    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        final KGroupedStream<String, LoginEvent> appOneGrouped =
                builder.stream(APP_ONE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .peek((key, value) -> LOG.info("App one input key[{}] value[{}]", key, value))
                .groupByKey();
        final KGroupedStream<String, LoginEvent> appTwoGrouped =
                builder.stream(APP_TWO_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .peek((key, value) -> LOG.info("App two input key[{}] value[{}]", key, value))
                .groupByKey();
        final KGroupedStream<String, LoginEvent> appThreeGrouped =
                builder.stream(APP_THREE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .peek((key, value) -> LOG.info("App three input key[{}] value[{}]", key, value))
                .groupByKey();

        final Aggregator<String, LoginEvent, LoginRollup> loginAggregator = new LoginAggregator();

        appOneGrouped.cogroup(loginAggregator)
                .cogroup(appTwoGrouped, loginAggregator)
                .cogroup(appThreeGrouped, loginAggregator)
                .aggregate(() -> new LoginRollup(new HashMap<>()), Materialized.with(Serdes.String(), loginRollupSerde))
                .toStream()
                .peek((key, value) -> LOG.info("CoGrouping results key[{}] value[{}]", key, value))
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, loginRollupSerde));

        return builder.build(allProps);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "cogrouping-streams");
        CogroupingStreams cogroupingStreams = new CogroupingStreams();

        Topology topology = cogroupingStreams.buildTopology(properties);

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
