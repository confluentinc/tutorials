package io.confluent.developer;

import io.confluent.developer.proto.PurchaseProto.Purchase;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

public class ProtoConsumerApp {

    public void close() {
        ExecutorService executorService = null;
        executorService.shutdown();
    }

    public ConsumerRecords<String, Purchase>  consumePurchaseEvents()  {
         Properties properties = loadProperties();
         Map<String, Object> protoConsumerConfigs = new HashMap<>();

         properties.forEach((key, value) -> protoConsumerConfigs.put((String) key, value));
         protoConsumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "schema-registry-course-consumer");
         protoConsumerConfigs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

         protoConsumerConfigs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
         protoConsumerConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class);
         protoConsumerConfigs.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, Purchase.class);

         Consumer<String, Purchase> protoConsumer = new KafkaConsumer<>(protoConsumerConfigs);
         protoConsumer.subscribe(Collections.singletonList("proto-purchase"));

         ConsumerRecords<String, Purchase> protoConsumerRecords = protoConsumer.poll(Duration.ofSeconds(2));
         protoConsumerRecords.forEach(protoConsumerRecord -> {
             Purchase protoPurchase = protoConsumerRecord.value();
             System.out.print("Purchase details consumed from topic with Protobuf schema { ");
             System.out.printf("Customer: %s, ", protoPurchase.getCustomerId());
             System.out.printf("Total Cost: %f, ", protoPurchase.getTotalCost());
             System.out.printf("Item: %s } %n", protoPurchase.getItem());
         });
            return protoConsumerRecords;
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
             ProtoConsumerApp consumerApp = new ProtoConsumerApp();
             consumerApp.consumePurchaseEvents();
         }
     }
