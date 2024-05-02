package io.confluent.developer;


import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class NamingChangelogAndRepartitionTopics {

  private static final Logger LOG = LoggerFactory.getLogger(NamingChangelogAndRepartitionTopics.class);
  public static final String INPUT_TOPIC = "input-topic";
  public static final String OUTPUT_TOPIC = "output-topic";
  public static final String JOIN_TOPIC = "join-topic";

  public Topology buildTopology(Properties properties) {
    final Serde<Long> longSerde = Serdes.Long();
    final Serde<String> stringSerde = Serdes.String();

    final boolean addFilter = Boolean.parseBoolean(properties.getProperty("add.filter"));
    final boolean addNames = Boolean.parseBoolean(properties.getProperty("add.names"));

    final StreamsBuilder builder = new StreamsBuilder();

    KStream<Long, String> inputStream = builder.stream(INPUT_TOPIC, Consumed.with(longSerde, stringSerde))
            .selectKey((k, v) -> Long.parseLong(v.substring(0, 1)));
    if (addFilter) {
      inputStream = inputStream.filter((k, v) -> k != 100L);
    }

    final KStream<Long, String> joinedStream;
    final KStream<Long, Long> countStream;

    if (!addNames) {
      countStream = inputStream.groupByKey(Grouped.with(longSerde, stringSerde))
              .count()
              .toStream();

      joinedStream = inputStream.join(countStream, (v1, v2) -> v1 + v2.toString(),
              JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMillis(100)),
              StreamJoined.with(longSerde, stringSerde, longSerde));
    } else {
      countStream = inputStream.groupByKey(Grouped.with("count", longSerde, stringSerde))
              .count(Materialized.as("the-counting-store"))
              .toStream();

      joinedStream = inputStream.join(countStream, (v1, v2) -> v1 + v2.toString(),
              JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMillis(100)),
              StreamJoined.with(longSerde, stringSerde, longSerde)
                      .withName("join").withStoreName("the-join-store"));
    }

    joinedStream.to(JOIN_TOPIC, Produced.with(longSerde, stringSerde));
    countStream.map((k, v) -> KeyValue.pair(k.toString(), v.toString())).to(OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));


    return builder.build();
  }

  public static void main(String[] args) {
    Properties properties;
    if (args.length > 0) {
      properties = Utils.loadProperties(args[0]);
    } else {
      properties = Utils.loadProperties();
    }
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "naming-changelog-operations-application");

    if (args.length > 1) {
      final String namesAndFilter = args[1];

      if (namesAndFilter.contains("filter")) {
        properties.put("add.filter", "true");
      }

      if (namesAndFilter.contains("names")) {
        properties.put("add.names", "true");
      }
    }

    final NamingChangelogAndRepartitionTopics instance = new NamingChangelogAndRepartitionTopics();
    Topology topology = instance.buildTopology(properties);

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
