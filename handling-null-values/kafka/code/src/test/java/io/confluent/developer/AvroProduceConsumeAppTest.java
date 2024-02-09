package io.confluent.developer;


import io.confluent.developer.avro.PurchaseAvro;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class AvroProduceConsumeAppTest {
    private static final Map<String, Object> commonConfigs = new HashMap<>();
    private static final Properties properties = new Properties();
    private final Serializer<String> stringSerializer = new StringSerializer();
    private AvroProducerApp avroProducerApp;
    private AvroConsumerApp avroConsumerApp;

    @BeforeClass
    public static void beforeAllTests() throws IOException {
        try (FileInputStream fis = new FileInputStream("configuration/test.properties")) {
            properties.load(fis);
            properties.forEach((key, value) -> commonConfigs.put((String) key, value));
        }
    }


    @Before
    public void setup() {
        avroProducerApp = new AvroProducerApp();
        avroConsumerApp = new AvroConsumerApp();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProduceAvroMultipleEvents() {
        KafkaAvroSerializer avroSerializer
                = new KafkaAvroSerializer();
        avroSerializer.configure(commonConfigs, false);
        MockProducer<String, SpecificRecordBase> mockAvroProducer
                = new MockProducer<String, SpecificRecordBase>(true, stringSerializer, (Serializer) avroSerializer);

        List<PurchaseAvro> returnedAvroResults = avroProducerApp.producePurchaseEvents();

        returnedAvroResults.forEach(c ->
        {
            String purchaseAvroId = c.getCustomerId();
             String purchaseItem = c.getItem();
            assertEquals("Customer Null", purchaseAvroId);
            assertEquals(null, purchaseItem);
            assertEquals(returnedAvroResults.size(), 2);
        });

    }

    @Test
    public void testConsumeAvroEvents() {
        MockConsumer<String, PurchaseAvro> mockConsumer = new MockConsumer<>(OffsetResetStrategy.LATEST);
        String topic = (String) commonConfigs.get("avro.topic");

        mockConsumer.schedulePollTask(() -> {
            addTopicPartitionsAssignment(topic, mockConsumer);
            addConsumerRecords(mockConsumer, avroProducerApp.producePurchaseEvents(), PurchaseAvro::getCustomerId, topic);
        });

        ConsumerRecords<String,PurchaseAvro> returnedAvroResults = avroConsumerApp.consumePurchaseEvents();
        List<PurchaseAvro> actualAvroResults = new ArrayList<>();
        returnedAvroResults.forEach(c ->
        {
            PurchaseAvro purchaseAvro = c.value();
            assertEquals("Customer Null", purchaseAvro.getCustomerId());
            assertEquals(null,purchaseAvro.getItem());
            assertEquals(actualAvroResults.size(), 2);
        });

        mockConsumer.schedulePollTask(() -> avroConsumerApp.close());
    }

    private <K, V> KeyValue<K, V> toKeyValue(final ProducerRecord<K, V> producerRecord) {
        return KeyValue.pair(producerRecord.key(), producerRecord.value());
    }

    private <V> void addTopicPartitionsAssignment(final String topic,
                                                  final MockConsumer<String, V> mockConsumer) {
        final TopicPartition topicPartition = new TopicPartition(topic, 0);
        final Map<TopicPartition, Long> beginningOffsets = new HashMap<>();
        beginningOffsets.put(topicPartition, 0L);
        mockConsumer.rebalance(Collections.singletonList(topicPartition));
        mockConsumer.updateBeginningOffsets(beginningOffsets);
    }

    private <V> void addConsumerRecords(final MockConsumer<String, V> mockConsumer,
                                        final List<V> records,
                                        final Function<V, String> keyFunction,
                                        final String topic) {
        AtomicInteger offset = new AtomicInteger(0);
        records.stream()
                .map(r -> new ConsumerRecord<>(topic, 0, offset.getAndIncrement(), keyFunction.apply(r), r))
                .forEach(mockConsumer::addRecord);
    }
}