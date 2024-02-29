package io.confluent.developer;


import io.confluent.developer.proto.Purchase;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ProtobufProduceConsumeAppTest {
    private static final Map<String, Object> commonConfigs = new HashMap<>();
    private static final Properties properties = new Properties();
    private final Serializer<String> stringSerializer = new StringSerializer();
    private ProtoProducerApp protoProducerApp;
    private ProtoConsumerApp protoConsumerApp;

    @BeforeClass
    public static void beforeAllTests() throws IOException {
        
        try (FileInputStream fis = new FileInputStream("resources/test.properties")) {
            properties.load(fis);
            properties.forEach((key, value) -> commonConfigs.put((String) key, value));
        }
    }


    @Before
    public void setup() {
        protoProducerApp = new ProtoProducerApp();
        protoConsumerApp = new ProtoConsumerApp();
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testProduceProtoMultipleEvents() {
        KafkaProtobufSerializer protoSerializer
                = new KafkaProtobufSerializer();
        protoSerializer.configure(commonConfigs, false);
        MockProducer<String, SpecificRecordBase> mockProtoProducer
                = new MockProducer<String, SpecificRecordBase>(true, stringSerializer, (Serializer) protoSerializer);

        List<Purchase> actualKeyValues = protoProducerApp.producePurchaseEvents();

        List<Purchase> returnedAvroResults = protoProducerApp.producePurchaseEvents();

        List<KeyValue<String, SpecificRecordBase>> expectedKeyValues =
                mockProtoProducer.history().stream().map(this::toKeyValue).collect(Collectors.toList());

        returnedAvroResults.forEach(c ->
        {
            String purchaseProtoId = c.getCustomerId();
            assertEquals("Customer Null", purchaseProtoId);
            assertEquals(actualKeyValues.size(), 2);
        });

    }

    @Test
    public void testConsumeProtoEvents() {
        MockConsumer<String, Purchase> mockConsumer = new MockConsumer<>(OffsetResetStrategy.LATEST);
        String topic = (String) commonConfigs.get("proto.topic");

        mockConsumer.schedulePollTask(() -> {
            addTopicPartitionsAssignment(topic, mockConsumer);
            addConsumerRecords(mockConsumer, protoProducerApp.producePurchaseEvents(), Purchase::getCustomerId, topic);
        });

        ConsumerRecords<String,Purchase> returnedProtoResults = protoConsumerApp.consumePurchaseEvents();
        List<Purchase> actualProtoResults = new ArrayList<>();
        returnedProtoResults.forEach(c ->
        {
            Purchase purchaseProto = c.value();
            assertEquals("Customer Null", purchaseProto.getCustomerId());
            assertEquals(null, purchaseProto.getItem());
            assertEquals(actualProtoResults.size(), 2);
        });

        mockConsumer.schedulePollTask(() -> protoConsumerApp.close());

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