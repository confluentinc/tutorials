package io.confluent.developer;



import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
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

public class StreamsToTable {


    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = allProps.getProperty("input.topic.name");
        final String streamsOutputTopic = allProps.getProperty("streams.output.topic.name");
        final String tableOutputTopic = allProps.getProperty("table.output.topic.name");

        final Serde<String> stringSerde = Serdes.String();

        final KStream<String, String> stream = builder.stream(inputTopic, Consumed.with(stringSerde, stringSerde));

        final KTable<String, String> convertedTable = stream.toTable(Materialized.as("stream-converted-to-table"));

        stream.to(streamsOutputTopic, Produced.with(stringSerde, stringSerde));
        convertedTable.toStream().to(tableOutputTopic, Produced.with(stringSerde, stringSerde));


        return builder.build();
    }


    public void createTopics(final Properties allProps) {
        try (final AdminClient client = AdminClient.create(allProps)) {

        final List<NewTopic> topics = new ArrayList<>();

            topics.add(new NewTopic(
                    allProps.getProperty("input.topic.name"),
                    Integer.parseInt(allProps.getProperty("input.topic.partitions")),
                    Short.parseShort(allProps.getProperty("input.topic.replication.factor"))));

            topics.add(new NewTopic(
                    allProps.getProperty("streams.output.topic.name"),
                    Integer.parseInt(allProps.getProperty("streams.output.topic.partitions")),
                    Short.parseShort(allProps.getProperty("streams.output.topic.replication.factor"))));

            topics.add(new NewTopic(
                allProps.getProperty("table.output.topic.name"),
                Integer.parseInt(allProps.getProperty("table.output.topic.partitions")),
                Short.parseShort(allProps.getProperty("table.output.topic.replication.factor"))));

            client.createTopics(topics);
        }
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        final StreamsToTable instance = new StreamsToTable();

        final Properties allProps = new Properties();
        try (InputStream inputStream = new FileInputStream(args[0])) {
            allProps.load(inputStream);
        }
        allProps.put(StreamsConfig.APPLICATION_ID_CONFIG, allProps.getProperty("application.id"));
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        final Topology topology = instance.buildTopology(allProps);

        instance.createTopics(allProps);

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

}
