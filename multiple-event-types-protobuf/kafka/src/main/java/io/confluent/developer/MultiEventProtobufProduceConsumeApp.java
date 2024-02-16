package io.confluent.developer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.confluent.developer.proto.CustomerEventProto;
import io.confluent.developer.proto.PageviewProto;
import io.confluent.developer.proto.PurchaseProto;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;

public class MultiEventProtobufProduceConsumeApp implements AutoCloseable {

    public static final String TOPIC = "proto-events";
    public static final String CUSTOMER_ID = "wilecoyote";
    private static final Logger LOG = LoggerFactory.getLogger(MultiEventProtobufProduceConsumeApp.class);
    private volatile boolean keepConsumingProto = true;
    final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public void produceProtobufEvents(final Supplier<Producer<String, CustomerEventProto.CustomerEvent>> producerSupplier,
                                      final String topic,
                                      final List<CustomerEventProto.CustomerEvent> protoCustomerEvents) {

        try (Producer<String, CustomerEventProto.CustomerEvent> producer = producerSupplier.get()) {
            protoCustomerEvents.stream()
                    .map((event -> new ProducerRecord<>(topic, event.getId(), event)))
                    .forEach(producerRecord -> producer.send(producerRecord, ((metadata, exception) -> {
                        if (exception != null) {
                            LOG.error("Error Protobuf producing message", exception);
                        } else {
                            LOG.debug("Produced Protobuf record offset {} timestamp {}", metadata.offset(), metadata.timestamp());
                        }
                    })));
        }
    }

    public void consumeProtoEvents(final Supplier<Consumer<String, CustomerEventProto.CustomerEvent>> consumerSupplier,
                                   final String topic,
                                   final List<String> eventTracker) {

        try (Consumer<String, CustomerEventProto.CustomerEvent> eventConsumer = consumerSupplier.get()) {
            eventConsumer.subscribe(Collections.singletonList(topic));
            while (keepConsumingProto) {
                ConsumerRecords<String, CustomerEventProto.CustomerEvent> consumerRecords = eventConsumer.poll(Duration.ofSeconds(1));
                consumerRecords.forEach(consumerRec -> {
                    CustomerEventProto.CustomerEvent customerEvent = consumerRec.value();
                    switch (customerEvent.getActionCase()) {
                        case PURCHASE:
                            eventTracker.add(String.format("Protobuf Purchase event -> %s", customerEvent.getPurchase().getItem()));
                            break;
                        case PAGEVIEW:
                            eventTracker.add(String.format("Protobuf Pageview event -> %s", customerEvent.getPageview().getUrl()));
                            break;
                        default:
                            LOG.error("Unexpected type - this shouldn't happen");
                    }
                });
            }
        }
    }

    public List<CustomerEventProto.CustomerEvent> protobufEvents() {
        CustomerEventProto.CustomerEvent.Builder customerEventBuilder = CustomerEventProto.CustomerEvent.newBuilder();
        PageviewProto.Pageview.Builder pageviewBuilder = PageviewProto.Pageview.newBuilder();
        PurchaseProto.Purchase.Builder purchaseBuilder = PurchaseProto.Purchase.newBuilder();
        List<CustomerEventProto.CustomerEvent> events = new ArrayList<>();

        PageviewProto.Pageview pageview = pageviewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/traps").setIsSpecial(false).build();
        PageviewProto.Pageview pageviewII = pageviewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/bombs").setIsSpecial(false).build();
        PageviewProto.Pageview pageviewIII = pageviewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/bait").setIsSpecial(true).build();
        PurchaseProto.Purchase purchase = purchaseBuilder.setCustomerId(CUSTOMER_ID).setItem("road-runner-bait").setAmount(99.99).build();

        events.add(customerEventBuilder.setId(CUSTOMER_ID).setPageview(pageview).build());
        events.add(customerEventBuilder.setId(CUSTOMER_ID).setPageview(pageviewII).build());
        events.add(customerEventBuilder.setId(CUSTOMER_ID).setPageview(pageviewIII).build());
        events.add((customerEventBuilder.setId(CUSTOMER_ID).setPurchase(purchase)).build());

        return events;
    }

    @Override
    public void close() {
        keepConsumingProto = false;
        executorService.shutdown();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            LOG.error("Must provide the path to the properties");
        }
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(args[0])) {
            properties.load(fis);
        }

        Map<String, Object> commonConfigs = new HashMap<>();
        properties.forEach((key, value) -> commonConfigs.put((String) key, value));


        try (MultiEventProtobufProduceConsumeApp multiEventApp = new MultiEventProtobufProduceConsumeApp()) {

            LOG.info("Producing Protobuf events now");
            multiEventApp.produceProtobufEvents(() -> new KafkaProducer<>(protoProduceConfigs(commonConfigs)), TOPIC, multiEventApp.protobufEvents());

            List<String> protoEvents = new ArrayList<>();
            multiEventApp.executorService.submit(() -> multiEventApp.consumeProtoEvents(() -> new KafkaConsumer<>(protoConsumeConfigs(commonConfigs)), TOPIC, protoEvents));
            while (protoEvents.size() < 3) {
                Thread.sleep(100);
            }
            LOG.info("Consumed Proto Events {}", protoEvents);
        }
    }

    @NotNull
    static Map<String, Object> protoConsumeConfigs(Map<String, Object> commonConfigs) {
        Map<String, Object> protoConsumeConfigs = new HashMap<>(commonConfigs);
        protoConsumeConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "protobuf-consumer-group");
        protoConsumeConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class);
        protoConsumeConfigs.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, CustomerEventProto.CustomerEvent.class);
        return protoConsumeConfigs;
    }

    @NotNull
    static Map<String, Object> protoProduceConfigs(Map<String, Object> commonConfigs) {
        Map<String, Object> protoProduceConfigs = new HashMap<>(commonConfigs);
        protoProduceConfigs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
        return protoProduceConfigs;
    }
}
