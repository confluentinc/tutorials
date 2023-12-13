package io.confluent.developer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class StreamsUncaughtExceptionHandling {

    int counter = 0;

    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = allProps.getProperty("input.topic.name");
        final String outputTopic = allProps.getProperty("output.topic.name");

        builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
                .mapValues(value -> {
                    counter++;
                    if (counter == 2 || counter == 8 || counter == 15) {
                        throw new ProcessorException("It works on my box!!!");
                    }
                    return value.toUpperCase();
                })
                .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
        
        return builder.build();
    }

    public void createTopics(Properties allProps) {
        try (AdminClient client = AdminClient.create(allProps)) {
            List<NewTopic> topicList = new ArrayList<>();

            NewTopic sessionInput = new NewTopic(allProps.getProperty("input.topic.name"),
                    Integer.parseInt(allProps.getProperty("input.topic.partitions")),
                    Short.parseShort(allProps.getProperty("input.topic.replication.factor")));
            topicList.add(sessionInput);

            NewTopic counts = new NewTopic(allProps.getProperty("output.topic.name"),
                    Integer.parseInt(allProps.getProperty("output.topic.partitions")),
                    Short.parseShort(allProps.getProperty("output.topic.replication.factor")));

            topicList.add(counts);
            client.createTopics(topicList);
        }
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

        StreamsUncaughtExceptionHandling tw = new StreamsUncaughtExceptionHandling();
        Properties allProps = tw.loadEnvProperties(args[0]);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        // Change this to StreamsConfig.EXACTLY_ONCE to eliminate duplicates
        allProps.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        Topology topology = tw.buildTopology(allProps);

        tw.createTopics(allProps);
        TutorialDataGenerator dataGenerator = new TutorialDataGenerator(allProps);
        dataGenerator.generate();

        final int maxFailures = Integer.parseInt(allProps.getProperty("max.failures"));
        final long maxTimeInterval = Long.parseLong(allProps.getProperty("max.time.millis"));
        final KafkaStreams streams = new KafkaStreams(topology, allProps);
        final MaxFailuresUncaughtExceptionHandler exceptionHandler = new MaxFailuresUncaughtExceptionHandler(maxFailures, maxTimeInterval);
        streams.setUncaughtExceptionHandler(exceptionHandler);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close(Duration.ofSeconds(5));
            }
        });

        try {
            streams.cleanUp();
            streams.start();
        } catch (Throwable e) {
            System.exit(1);
        }
    }

    static class TutorialDataGenerator {
        final Properties properties;


        public TutorialDataGenerator(final Properties properties) {
            this.properties = properties;
        }

        public void generate() {
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

            try (Producer<String, String> producer = new KafkaProducer<>(properties)) {
                String topic = properties.getProperty("input.topic.name");
                List<String> messages = Arrays.asList("All", "streams", "lead", "to", "Confluent", "Go", "to", "Kafka", "Summit");


                messages.forEach(message -> producer.send(new ProducerRecord<>(topic, message), (metadata, exception) -> {
                        if (exception != null) {
                            exception.printStackTrace(System.out);
                        } else {
                            System.out.printf("Produced record (%s) at offset %d to topic %s %n", message, metadata.offset(), metadata.topic());
                        }
                }));
            }
        }
    }
}
