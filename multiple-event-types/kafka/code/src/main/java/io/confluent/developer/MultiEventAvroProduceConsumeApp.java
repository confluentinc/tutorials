package io.confluent.developer;

import io.confluent.developer.avro.PageView;
import io.confluent.developer.avro.Purchase;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
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

public class MultiEventAvroProduceConsumeApp implements AutoCloseable{

    public static final String CUSTOMER_ID = "wilecoyote";
    private static final Logger LOG = LoggerFactory.getLogger(MultiEventAvroProduceConsumeApp.class);
    private volatile boolean keepConsumingAvro = true;
    final ExecutorService executorService = Executors.newFixedThreadPool(1);

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
                        eventTracker.add(String.format("Avro Purchase event -> %s",purchase.getItem()));
                    } else if (avroRecord instanceof PageView) {
                        PageView pageView = (PageView) avroRecord;
                        eventTracker.add(String.format("Avro Pageview event -> %s",pageView.getUrl()));
                    } else {
                        LOG.error("Unexpected type - this shouldn't happen");
                    }
                });
            }
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

    @Override
    public void close() {
        keepConsumingAvro = false;
        executorService.shutdown();
    }


    public void createTopics(final Properties allProps) {
        try (final AdminClient client = AdminClient.create(allProps)) {

            final List<NewTopic> topics = new ArrayList<>();
            
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


        try (MultiEventAvroProduceConsumeApp multiEventApp = new MultiEventAvroProduceConsumeApp()) {
            multiEventApp.createTopics(properties);
            String avroTopic = (String) commonConfigs.get("avro.topic.name");

            LOG.info("Producing Avro events");
            multiEventApp.produceAvroEvents(() -> new KafkaProducer<>(avroProduceConfigs(commonConfigs)), avroTopic, multiEventApp.avroEvents());
            
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
}
