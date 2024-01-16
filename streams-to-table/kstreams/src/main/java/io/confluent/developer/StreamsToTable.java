package io.confluent.developer;


import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class StreamsToTable {

    public static final String INPUT_TOPIC = "input-topic";
    public static final String STREAMS_OUTPUT_TOPIC = "streams-output-topic";
    public static final String TABLE_OUTPUT_TOPIC = "table-output-topic";


    public Topology buildTopology(Properties properties) {
        final Serde<String> stringSerde = Serdes.String();
        final StreamsBuilder builder = new StreamsBuilder();

        final KStream<String, String> stream = builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde));

        final KTable<String, String> convertedTable = builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde))
                .toTable(Materialized.as("stream-converted-to-table"));

        /*final KStream<String, String> stream = builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, stringSerde));

        final KTable<String, String> convertedTable = stream.toTable(Materialized.as("stream-converted-to-table"));
*/
        stream.to(STREAMS_OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));
        convertedTable.toStream().to(TABLE_OUTPUT_TOPIC, Produced.with(stringSerde, stringSerde));

        return builder.build(properties);
    }


    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-streams-transforming");

        StreamsToTable streamsToTable = new StreamsToTable();
        Topology topology = streamsToTable.buildTopology(properties);

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
