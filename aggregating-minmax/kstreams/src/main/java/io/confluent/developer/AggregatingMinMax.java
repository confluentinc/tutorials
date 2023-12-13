package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class AggregatingMinMax {

     private static final Logger LOG = LoggerFactory.getLogger(AggregatingMinMax.class);
     public static final String INPUT_TOPIC = "min-max-input";
     public static final String OUTPUT_TOPIC = "min-max-output";

     private final Serde<MovieTicketSales> movieSalesSerde = StreamsSerde.serdeFor(MovieTicketSales.class);
     private final Serde<YearlyMovieFigures> yearlySalesSerde = StreamsSerde.serdeFor(YearlyMovieFigures.class);

     public Topology buildTopology(final Properties allProps) {
          StreamsBuilder builder = new StreamsBuilder();
          builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), movieSalesSerde))
                  .peek((key, value) -> LOG.info("Incoming data key[{}] value[{}]", key, value))
                  .groupBy(
                          (k, v) -> v.releaseYear(),
                          Grouped.with(Serdes.Integer(), movieSalesSerde))
                  .aggregate(
                          () -> new YearlyMovieFigures(0, Integer.MAX_VALUE, Integer.MIN_VALUE),
                          ((key, value, aggregate) ->
                                  new YearlyMovieFigures(key,
                                          Math.min(value.totalSales(), aggregate.minTotalSales()),
                                          Math.max(value.totalSales(), aggregate.maxTotalSales()))),
                          Materialized.with(Serdes.Integer(), yearlySalesSerde))
                  .toStream()
                  .peek((key, value) -> LOG.info("Aggregation min-max results key[{}] value[{}]", key, value))
                  .to(OUTPUT_TOPIC, Produced.with(Serdes.Integer(), yearlySalesSerde));

          return builder.build(allProps);
     }

     public static void main(String[] args) {
          Properties properties;
          if (args.length > 0) {
               properties = Utils.loadProperties(args[0]);
          } else {
               properties = Utils.loadProperties();
          }
          properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "aggregating-min-max");
          AggregatingMinMax aggregatingMinMax = new AggregatingMinMax();

          Topology topology = aggregatingMinMax.buildTopology(properties);

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
