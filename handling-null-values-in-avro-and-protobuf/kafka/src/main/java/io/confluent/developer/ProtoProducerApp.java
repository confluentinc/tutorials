package io.confluent.developer;


import io.confluent.developer.proto.PurchaseProto;
import io.confluent.developer.proto.PurchaseProto.Purchase;
import io.confluent.developer.proto.PurchaseProto.Purchase.Builder;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class ProtoProducerApp {
    private static final Logger LOG = LoggerFactory.getLogger(ProtoProducerApp.class);
    private final Random random = new Random();
    public List<PurchaseProto.Purchase>  producePurchaseEvents() {
        Builder purchaseBuilder = Purchase.newBuilder();
        Properties properties = loadProperties();

        Map<String, Object> protoProducerConfigs = new HashMap<>();

        properties.forEach((key, value) -> protoProducerConfigs.put((String) key, value));

        protoProducerConfigs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        protoProducerConfigs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
        // Setting schema auto-registration to false since we already registered the schema manually following best practice
        protoProducerConfigs.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

        // Duplication of configs loaded from confluent.properties to emphasize what's needed to use SchemaRegistry
        protoProducerConfigs.put("schema.registry.url", "SR_URL");
        protoProducerConfigs.put("basic.auth.credentials.source", "USER_INFO");
        protoProducerConfigs.put("basic.auth.user.info", "KEY:SECRET");


        System.out.printf("Producer now configured for using SchemaRegistry %n");
        List<Purchase> protoPurchaseEvents = new ArrayList<>();

        try (final Producer<String, Purchase> producer = new KafkaProducer<>(protoProducerConfigs)) {
            String protoTopic = "proto-purchase";

            Purchase protoPurchase = getPurchaseObjectProto(purchaseBuilder);
            Purchase protoPurchaseII = getPurchaseObjectProto(purchaseBuilder);

            protoPurchaseEvents.add(protoPurchase);
            protoPurchaseEvents.add(protoPurchaseII);

            protoPurchaseEvents.forEach(event -> producer.send(new ProducerRecord<>(protoTopic, event.getCustomerId(), event), ((metadata, exception) -> {
                if (exception != null) {
                    System.err.printf("Producing %s resulted in error %s %n", event, exception);
                } else {
                    System.out.printf("Produced record to topic with Protobuf schema at offset %s with timestamp %d %n", metadata.offset(), metadata.timestamp());
                }
            })));

        }
        return protoPurchaseEvents;
    }



    Purchase getPurchaseObjectProto(Builder purchaseBuilder) {
        purchaseBuilder.clear();
        purchaseBuilder.setCustomerId("Customer Null")
                .setTotalCost(random.nextDouble() * random.nextInt(100));
        return purchaseBuilder.build();
    }

    Properties loadProperties() {
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

    public static void main(String[] args) {
        ProtoProducerApp producerApp = new ProtoProducerApp();
        producerApp.producePurchaseEvents();
    }
}