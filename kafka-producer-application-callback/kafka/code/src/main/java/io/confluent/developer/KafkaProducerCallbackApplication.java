package io.confluent.developer;


import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class KafkaProducerCallbackApplication {

    private final Producer<String, String> producer;
    final String outTopic;

    public KafkaProducerCallbackApplication(final Producer<String, String> producer,
                                    final String topic) {
        this.producer = producer;
        outTopic = topic;
    }

    public void produce(final String message) {
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
        producer.send(producerRecord, (recordMetadata, exception) -> {
              if (exception == null) {
                  System.out.println("Record written to offset " +
                          recordMetadata.offset() + " timestamp " +
                          recordMetadata.timestamp());
              } else {
                  System.err.println("An error occurred");
                  exception.printStackTrace(System.err);
              }
        });
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

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "This program takes two arguments: the path to an environment configuration file and" +
                            "the path to the file with records to send");
        }

        final Properties props = KafkaProducerCallbackApplication.loadProperties(args[0]);
        final String topic = props.getProperty("output.topic.name");
        final Producer<String, String> producer = new KafkaProducer<>(props);
        final KafkaProducerCallbackApplication producerApp = new KafkaProducerCallbackApplication(producer, topic);
        
        String filePath = args[1];
        try {
            List<String> linesToProduce = Files.readAllLines(Paths.get(filePath));
            linesToProduce.stream()
                          .filter(l -> !l.trim().isEmpty())
                          .forEach(producerApp::produce);
        } catch (IOException e) {
            System.err.printf("Error reading file %s due to %s %n", filePath, e);
        } finally {
           producerApp.shutdown();
        }
    }
}
