package io.confluent.developer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.Random;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

public class AvroProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AvroProducer.class);

    public static void main(String[] args) {

        if (args.length != 2) {
            LOG.error("Program requires 2 arguments: path to Kafka properties file and path to schema file");
            System.exit(1);
        }

        Properties properties = Utils.loadProperties(args[0]);

        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());

        Schema schema = null;
        try {
            schema = new Schema.Parser().parse(new File(args[1]));
        } catch (IOException e) {
            LOG.error("Error parsing schema file", e);
            System.exit(1);
        }

        KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(properties);

        Random rand = new Random();
        for (int i = 0; i < 10; i++) {
            String deviceId = String.valueOf(rand.nextInt(5));
            float randTemp = 70.0f + 30.0f * rand.nextFloat();

            GenericRecord reading = new GenericData.Record(schema);
            reading.put("deviceId", deviceId);
            reading.put("temperature", randTemp);

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
