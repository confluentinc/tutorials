package io.confluent.developer;

import io.confluent.developer.avro.PurchaseAvro;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Properties;

public class AvroConsumerApp implements AutoCloseable{

    public void close() {
        keepConsumingAvro = false;
        ExecutorService executorService = null;
        executorService.shutdown();
    }
    private volatile boolean keepConsumingAvro = true;

        public ConsumerRecords<String, PurchaseAvro> consumePurchaseEvents() {
            Properties properties = loadProperties();

            Map<String, Object> avroConsumerConfigs = new HashMap<>();


            properties.forEach((key, value) -> avroConsumerConfigs.put((String) key, value));
            avroConsumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "schema-registry-course-consumer");
            avroConsumerConfigs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            avroConsumerConfigs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            avroConsumerConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
            avroConsumerConfigs.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);


            // Duplication of configs loaded from confluent.properties to emphasize what's needed to use SchemaRegistry
            avroConsumerConfigs.put("schema.registry.url", "SR_URL");
            avroConsumerConfigs.put("basic.auth.credentials.source", "USER_INFO");
            avroConsumerConfigs.put("basic.auth.user.info", "KEY:SECRET");

            Consumer<String, PurchaseAvro> avroConsumer = new KafkaConsumer<>(avroConsumerConfigs);

            avroConsumer.subscribe(Collections.singletonList("avro-purchase"));

            ConsumerRecords<String, PurchaseAvro> avroConsumerRecords = avroConsumer.poll(Duration.ofSeconds(2));
            avroConsumerRecords.forEach(avroConsumerRecord -> {
                PurchaseAvro avroPurchase = avroConsumerRecord.value();
                System.out.print("Purchase details consumed from topic with Avro schema { ");
                System.out.printf("Customer: %s, ", avroPurchase.getCustomerId());
                System.out.printf("Total Cost: %f, ", avroPurchase.getTotalCost());
                System.out.printf("Item: %s } %n", avroPurchase.getItem());

            });
            return avroConsumerRecords;
        }


        Properties loadProperties () {
            try (InputStream inputStream = this.getClass()
                    .getClassLoader()
                    .getResourceAsStream("confluent.properties")) {
                Properties props = new Properties();
                props.load(inputStream);
                return props;
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }


        public static void main (String[]args){
            io.confluent.developer.AvroConsumerApp consumerApp = new io.confluent.developer.AvroConsumerApp();
            consumerApp.consumePurchaseEvents();
        }



}