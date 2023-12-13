package io.confluent.developer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.BranchedKStream;
import org.apache.kafka.streams.kstream.Branched;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.developer.avro.ActingEvent;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

public class SplitStream {

    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = allProps.getProperty("input.topic.name");

        builder.<String, ActingEvent>stream(inputTopic)
              .split()
              .branch(
                   (key, appearance) -> "drama".equals(appearance.getGenre()),
                   Branched.withConsumer(ks -> ks.to(allProps.getProperty("output.drama.topic.name"))))
              .branch(
                   (key, appearance) -> "fantasy".equals(appearance.getGenre()),
                   Branched.withConsumer(ks -> ks.to(allProps.getProperty("output.fantasy.topic.name"))))
              .branch(
                   (key, appearance) -> true,
                   Branched.withConsumer(ks -> ks.to(allProps.getProperty("output.other.topic.name"))));

        return builder.build();
    }

    public void createTopics(Properties allProps) {
        AdminClient client = AdminClient.create(allProps);

        List<NewTopic> topics = new ArrayList<>();

        topics.add(new NewTopic(
                allProps.getProperty("input.topic.name"),
                Integer.parseInt(allProps.getProperty("input.topic.partitions")),
                Short.parseShort(allProps.getProperty("input.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("output.drama.topic.name"),
                Integer.parseInt(allProps.getProperty("output.drama.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.drama.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("output.fantasy.topic.name"),
                Integer.parseInt(allProps.getProperty("output.fantasy.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.fantasy.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("output.other.topic.name"),
                Integer.parseInt(allProps.getProperty("output.other.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.other.topic.replication.factor"))));

        client.createTopics(topics);
        client.close();
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

        SplitStream ss = new SplitStream();
        Properties allProps = ss.loadEnvProperties(args[0]);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);

        Topology topology = ss.buildTopology(allProps);
        ss.createTopics(allProps);

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
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
