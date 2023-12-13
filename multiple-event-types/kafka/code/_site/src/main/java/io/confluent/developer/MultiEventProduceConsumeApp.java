package io.confluent.developer;

import io.confluent.developer.avro.PageView;
import io.confluent.developer.avro.Purchase;
import io.confluent.developer.proto.CustomerEventProto;
import io.confluent.developer.proto.PageViewProto;
import io.confluent.developer.proto.PurchaseProto;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
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

public class MultiEventProduceConsumeApp implements AutoCloseable{

    public static final String CUSTOMER_ID = "wilecoyote";
    private static final Logger LOG = LoggerFactory.getLogger(MultiEventProduceConsumeApp.class);
    private volatile boolean keepConsumingAvro = true;
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

    public void produceAvroEvents(final Supplier<Producer<String, SpecificRecordBase>> producerSupplier,
                                  final String topic,
                                  final List<SpecificRecordBase> avroEvents) {

        try (Producer<String, SpecificRecordBase> producer = producerSupplier.get()) {
           avroEvents.stream()
                    .map((event -> new ProducerRecord<>(topic, (String) event.get("customer_id"), event)))
                    .forEach(producerRecord -> producer.send(producerRecord, ((metadata, exception) -> {
                        if (exception != null) {
                            LOG.error("Error Avro producing message", exception);
                        } else {
                            LOG.debug("Produced Avro record offset {} timestamp {}", metadata.offset(), metadata.timestamp());
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
                            eventTracker.add(customerEvent.getPurchase().getItem());
                            break;
                        case PAGE_VIEW:
                            eventTracker.add(customerEvent.getPageView().getUrl());
                            break;
                        default:
                            LOG.error("Unexpected type - this shouldn't happen");
                    }
                });
            }
            LOG.info("Protobuf consumer loop stopped now");
        }
    }

    public void consumeAvroEvents(final Supplier<Consumer<String, SpecificRecordBase>> consumerSupplier,
                                  final String topic,
                                  final List<String> eventTracker) {
        try (Consumer<String, SpecificRecordBase> eventConsumer = consumerSupplier.get()) {
            eventConsumer.subscribe(Collections.singletonList(topic));
            while (keepConsumingAvro) {
                ConsumerRecords<String, SpecificRecordBase> consumerRecords = eventConsumer.poll(Duration.ofSeconds(1));
                consumerRecords.forEach(consumerRec -> {
                    SpecificRecord avroRecord = consumerRec.value();
                    if (avroRecord instanceof Purchase) {
                        Purchase purchase = (Purchase) avroRecord;
                        eventTracker.add(purchase.getItem());
                    } else if (avroRecord instanceof PageView) {
                        PageView pageView = (PageView) avroRecord;
                        eventTracker.add(pageView.getUrl());
                    } else {
                        LOG.error("Unexpected type - this shouldn't happen");
                    }
                });
            }
            LOG.info("Avro consumer loop stopped now");
        }
    }


    public List<SpecificRecordBase> avroEvents() {
        PageView.Builder pageViewBuilder = PageView.newBuilder();
        Purchase.Builder purchaseBuilder = Purchase.newBuilder();
        List<SpecificRecordBase> events = new ArrayList<>();

        PageView pageView = pageViewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/traps").setIsSpecial(false).build();
        PageView pageViewII = pageViewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/bombs").setIsSpecial(false).build();
        PageView pageViewIII = pageViewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/bait").setIsSpecial(true).build();
        Purchase purchase = purchaseBuilder.setCustomerId(CUSTOMER_ID).setItem("road-runner-bait").setAmount(99.99).build();

        events.add(pageView);
        events.add(pageViewII);
        events.add(pageViewIII);
        events.add(purchase);

        return events;
    }

    public List<CustomerEventProto.CustomerEvent> protobufEvents() {
        CustomerEventProto.CustomerEvent.Builder customerEventBuilder = CustomerEventProto.CustomerEvent.newBuilder();
        PageViewProto.PageView.Builder pageViewBuilder = PageViewProto.PageView.newBuilder();
        PurchaseProto.Purchase.Builder purchaseBuilder = PurchaseProto.Purchase.newBuilder();
        List<CustomerEventProto.CustomerEvent> events = new ArrayList<>();

        PageViewProto.PageView pageView = pageViewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/traps").setIsSpecial(false).build();
        PageViewProto.PageView pageViewII = pageViewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/bombs").setIsSpecial(false).build();
        PageViewProto.PageView pageViewIII = pageViewBuilder.setCustomerId(CUSTOMER_ID).setUrl("http://acme/bait").setIsSpecial(true).build();
        PurchaseProto.Purchase purchase = purchaseBuilder.setCustomerId(CUSTOMER_ID).setItem("road-runner-bait").setAmount(99.99).build();

        events.add(customerEventBuilder.setId(CUSTOMER_ID).setPageView(pageView).build());
        events.add(customerEventBuilder.setId(CUSTOMER_ID).setPageView(pageViewII).build());
        events.add(customerEventBuilder.setId(CUSTOMER_ID).setPageView(pageViewIII).build());
        events.add((customerEventBuilder.setId(CUSTOMER_ID).setPurchase(purchase)).build());

        return events;
    }

    @Override
    public void close() {
        keepConsumingProto = false;
        keepConsumingAvro = false;
        executorService.shutdown();
    }


    public void createTopics(final Properties allProps) {
        try (final AdminClient client = AdminClient.create(allProps)) {

            final List<NewTopic> topics = new ArrayList<>();

            topics.add(new NewTopic(
                    allProps.getProperty("proto.topic.name"),
                    Integer.parseInt(allProps.getProperty("proto.topic.partitions")),
                    Short.parseShort(allProps.getProperty("proto.topic.replication.factor"))));

            topics.add(new NewTopic(
                    allProps.getProperty("avro.topic.name"),
                    Integer.parseInt(allProps.getProperty("avro.topic.partitions")),
                    Short.parseShort(allProps.getProperty("avro.topic.replication.factor"))));

            client.createTopics(topics);
        }
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


        try (MultiEventProduceConsumeApp multiEventApp = new MultiEventProduceConsumeApp()) {
            multiEventApp.createTopics(properties);
            String protobufTopic = (String) commonConfigs.get("proto.topic.name");
            String avroTopic = (String) commonConfigs.get("avro.topic.name");

            LOG.info("Producing Protobuf events now");
            multiEventApp.produceProtobufEvents(() -> new KafkaProducer<>(protoProduceConfigs(commonConfigs)), protobufTopic, null);

            LOG.info("Consuming Protobuf events");
            List<String> protoEvents = new ArrayList<>();
            multiEventApp.executorService.submit(() -> multiEventApp.consumeProtoEvents(() -> new KafkaConsumer<>(protoConsumeConfigs(commonConfigs)), protobufTopic, protoEvents));
            while (protoEvents.size() < 3) {
                Thread.sleep(100);
            }
            LOG.info("Consumed Proto Events {}", protoEvents);


            LOG.info("Producing Avro events");
            multiEventApp.produceAvroEvents(() -> new KafkaProducer<>(avroProduceConfigs(commonConfigs)), avroTopic, null);

            LOG.info("Consuming Avro events");
            List<String> avroEvents = new ArrayList<>();

            multiEventApp.executorService.submit(() -> multiEventApp.consumeAvroEvents(() -> new KafkaConsumer<>(avroConsumeConfigs(commonConfigs)), avroTopic, avroEvents));
            while (avroEvents.size() < 3) {
                Thread.sleep(100);
            }

            LOG.info("Consumed Avro Events {}", avroEvents);
        }
    }


    @NotNull
    static Map<String, Object> avroConsumeConfigs(Map<String, Object> commonConfigs) {
        Map<String, Object> avroConsumeConfigs = new HashMap<>(commonConfigs);
        avroConsumeConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "avro-consumer-group");
        avroConsumeConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        avroConsumeConfigs.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return avroConsumeConfigs;
    }

    @NotNull
    static Map<String, Object> avroProduceConfigs(Map<String, Object> commonConfigs) {
        Map<String, Object> avroProduceConfigs = new HashMap<>(commonConfigs);
        avroProduceConfigs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        avroProduceConfigs.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);
        avroProduceConfigs.put(AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION, true);
        return avroProduceConfigs;
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
