package io.confluent.developer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static io.confluent.developer.ConsumerAppArgParser.streamFromFile;
import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

public class ProducerApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerApp.class);

    private static Properties getKafkaProperties(String kafkaProperties) {
        Properties properties = new Properties();
        try (InputStream inputStream = streamFromFile(kafkaProperties)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        properties.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        properties.put(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        properties.put(ACKS_CONFIG, "all");

        return properties;
    }

    public static void main(final String[] args) {
        ProducerAppArgParser cmdArgs = ProducerAppArgParser.parseOptions(args);
        Properties props = getKafkaProperties(cmdArgs.getKafkaProperties());
        final String topic = "strings";

        try (final Producer<String, String> producer = new KafkaProducer<>(props)) {
            final int numMessages = 1000000;
            for (Integer i = 0; i < numMessages; i++) {
                String message = i.toString();
                final int finalI = i;
                producer.send(
                        new ProducerRecord<>(topic, message, message),
                        (event, ex) -> {
                            if (ex != null)
                                ex.printStackTrace();
                            else
                            if (finalI % 10000 == 0)
                                LOGGER.info("Produced {} of {} events so far", finalI, numMessages);
                        });
                producer.flush();
                if (i % 1000 == 0) {
                    Thread.sleep(100);
                }
            }
            System.out.printf("%s events were produced to topic %s%n", numMessages, topic);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
