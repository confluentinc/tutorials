package io.confluent.developer;


import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class KafkaProducerApplication {

    private final Producer<String, String> producer;
    public KafkaProducerApplication(final Producer<String, String> producer) {
        this.producer = producer;
    }

    public KafkaProducerApplication(final Properties properties) {
        this.producer = new KafkaProducer<>(properties);
    }

    @Deprecated
    public Future<RecordMetadata> produce(final String outTopic, final String message) {
        final String[] parts = message.split("-");
        final String key, value;
        if (parts.length > 1) {
            key = parts[0];
            value = parts[1];
        } else {
            key = null;
            value = parts[0];
        }
        final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(outTopic, key, value);
        return producer.send(producerRecord);
    }

    public void shutdown() {
        producer.close();
    }

    public static Properties loadProperties(String fileName) throws IOException {
        final Properties envProps = new Properties();
        final FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public void printMetadata(final Collection<Future<RecordMetadata>> metadata,
                              final String fileName) {
        System.out.println("Offsets and timestamps committed in batch from " + fileName);
        metadata.forEach(m -> {
            try {
                final RecordMetadata recordMetadata = m.get();
                System.out.println("Record written to offset " + recordMetadata.offset() + " timestamp " + recordMetadata.timestamp());
            } catch (InterruptedException | ExecutionException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: this program takes 2 arguments: \n" +
                            "1) list of kafka brokers (host:port) \n" +
                            "2) the path to the file with records to send ");
        }

        final String bootstrapServers = args[0];
//Utils.loadProperties(args[1]);
        Properties props = new Properties() {{
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            put(ProducerConfig.ACKS_CONFIG, "1");
        }};

        final String topic = "output-topic";
        final KafkaProducerApplication producerApp = new KafkaProducerApplication(props);

        String filePath = args[1];
        try {
            List<String> linesToProduce = Files.readAllLines(Paths.get(filePath));
            List<Future<RecordMetadata>> metadata = linesToProduce.stream()
                    .filter(l -> !l.trim().isEmpty())
                    .map(e -> producerApp.createRecord(topic, e))
                    .map(producerApp::sendEvent)
                    .collect(Collectors.toList());
            producerApp.printMetadata(metadata, filePath);

        } catch (IOException e) {
            System.err.printf("Error reading file %s due to %s %n", filePath, e);
        } finally {
            producerApp.shutdown();
        }
    }

    public Future<RecordMetadata> sendEvent(final ProducerRecord<String, String> record) {
        return producer.send(record);
    }

    public Future<RecordMetadata> sendEvent(final ProducerRecord<String, String> record, final Callback callback) {
        return producer.send(record, callback);
    }

    /**
     * Convert raw `-` delimited String to a {@link ProducerRecord}.
     *
     * @param topic
     * @param message
     * @return First token is KEY, second token is VALUE of the resulting record.
     */
    public ProducerRecord<String, String> createRecord(final String topic, final String message) {
        final String[] parts = message.split("-");
        final String key, value;
        if (parts.length > 1) {
            key = parts[0];
            value = parts[1];
        } else {
            key = null;
            value = parts[0];
        }
        return new ProducerRecord<>(topic, key, value);
    }
}
