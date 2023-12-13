package io.confluent.developer;


import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.common.serialization.LongSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import io.confluent.developer.avro.DeviceEvent;

public class KafkaProducerDevice {

    private final Producer<Long, DeviceEvent> producer;
    final String outTopic;

    public KafkaProducerDevice(final Producer<Long, DeviceEvent> producer,
                                    final String topic) {
        this.producer = producer;
        outTopic = topic;
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
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "This program takes one argument: the path to an environment configuration file");
        }

        final Properties props = KafkaProducerDevice.loadProperties(args[0]);

        props.put(ProducerConfig.ACKS_CONFIG, "all");

        props.put(ProducerConfig.CLIENT_ID_CONFIG, "myEventApp");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

        final String topic = props.getProperty("output.topic.name");
        final Producer<Long, DeviceEvent> producer = new KafkaProducer<Long, DeviceEvent>(props);
        final KafkaProducerDevice producerApp = new KafkaProducerDevice(producer, topic);

        final Long deviceId = 1L;
        Long temperature = 100L;
        Long eventTime;

        int count = 0;

        while(count < 10) {

            eventTime = System.currentTimeMillis();
            // Inject artificial delay before record is produced to Kafka
            // to force differing timestamps in payload and metadata
            Thread.sleep(1005);

            DeviceEvent record = new DeviceEvent(temperature, eventTime);
            final ProducerRecord<Long, DeviceEvent> producerRecord = new ProducerRecord<>(topic, deviceId, record);
            producer.send(producerRecord,
                (recordMetadata, e) -> {
                    if(e != null) {
                       e.printStackTrace();
                    } else {
                      System.out.println("Record written to topic " + recordMetadata.topic() + ": payload eventTime " + record.getEventTime() + ", Kafka timestamp " + recordMetadata.timestamp());
                    }
                  }
                );

            count++;
            temperature++;

        }

        producerApp.shutdown();

    }
}
