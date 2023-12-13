package io.confluent.developer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.developer.avro.SongEvent;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

public class MergeStreams {

    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        final String rockTopic = allProps.getProperty("input.rock.topic.name");
        final String classicalTopic = allProps.getProperty("input.classical.topic.name");
        final String allGenresTopic = allProps.getProperty("output.topic.name");

        KStream<String, SongEvent> rockSongs = builder.stream(rockTopic);
        KStream<String, SongEvent> classicalSongs = builder.stream(classicalTopic);
        KStream<String, SongEvent> allSongs = rockSongs.merge(classicalSongs);

        allSongs.to(allGenresTopic);
        return builder.build();
    }

    public void createTopics(Properties allProps) {
        AdminClient client = AdminClient.create(allProps);

        List<NewTopic> topics = new ArrayList<>();

        topics.add(new NewTopic(
                allProps.getProperty("input.rock.topic.name"),
                Integer.parseInt(allProps.getProperty("input.rock.topic.partitions")),
                Short.parseShort(allProps.getProperty("input.rock.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("input.classical.topic.name"),
                Integer.parseInt(allProps.getProperty("input.classical.topic.partitions")),
                Short.parseShort(allProps.getProperty("input.classical.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("output.topic.name"),
                Integer.parseInt(allProps.getProperty("output.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.topic.replication.factor"))));

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

        MergeStreams ms = new MergeStreams();
        Properties allProps = ms.loadEnvProperties(args[0]);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        allProps.put(SCHEMA_REGISTRY_URL_CONFIG, allProps.getProperty("schema.registry.url"));
        Topology topology = ms.buildTopology(allProps);

        ms.createTopics(allProps);

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
