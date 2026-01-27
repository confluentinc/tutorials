package io.confluent.developer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;

import io.confluent.developer.avro.TempReading;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

public class AvroProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AvroProducer.class);

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }

        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());

        KafkaProducer<String, TempReading> producer = new KafkaProducer<>(properties);

        Random rand = new Random();
        for (int i = 0; i < 10; i++) {
            String deviceId = String.valueOf(rand.nextInt(5));
            float randTemp = 70.0f + 30.0f * rand.nextFloat();
            TempReading reading = new TempReading(deviceId, randTemp);
            producer.send(
                    new ProducerRecord<>("readings", deviceId, reading),
                    (event, ex) -> {
                        if (ex != null)
                            LOG.error("Error producing event", ex);
                        else
                            LOG.info("Produced event: key = {}, value = {}", deviceId, reading);
                    });
        }

        producer.flush();
        producer.close();
    }

}
