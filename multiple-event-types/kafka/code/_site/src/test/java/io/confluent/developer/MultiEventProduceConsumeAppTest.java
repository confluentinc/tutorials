package io.confluent.developer;

import io.confluent.developer.proto.CustomerEventProto;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class MultiEventProduceConsumeAppTest {
    private static final Map<String, Object> commonConfigs = new HashMap<>();
    private static final Properties properties = new Properties();
    private final Serializer<String> stringSerializer = new StringSerializer();
    private MultiEventProduceConsumeApp produceConsumeApp;

    @BeforeClass
    public static void beforeAllTests() throws IOException {
        try (FileInputStream fis = new FileInputStream("configuration/test.properties")) {
            properties.load(fis);
            properties.forEach((key, value) -> commonConfigs.put((String) key, value));
        }
    }


    @Before
    public void setup() {
        produceConsumeApp = new MultiEventProduceConsumeApp();
    }

    @Test
    public void testProduceProtobufMultipleEvents() {
        KafkaProtobufSerializer<CustomerEventProto.CustomerEvent> protobufSerializer
                = new KafkaProtobufSerializer<>();
        protobufSerializer.configure(commonConfigs, false);
        MockProducer<String, CustomerEventProto.CustomerEvent> mockProtoProducer
                = new MockProducer<>(true, stringSerializer, protobufSerializer);
        List<CustomerEventProto.CustomerEvent> events = produceConsumeApp.protobufEvents();
        produceConsumeApp.produceProtobufEvents(() -> mockProtoProducer, (String) commonConfigs.get("proto.topic"), events);
        List<KeyValue<String, CustomerEventProto.CustomerEvent>> expectedKeyValues =
                produceConsumeApp.protobufEvents().stream().map((e -> KeyValue.pair(e.getId(), e))).collect(Collectors.toList());

        List<KeyValue<String, CustomerEventProto.CustomerEvent>> actualKeyValues =
                mockProtoProducer.history().stream().map(this::toKeyValue).collect(Collectors.toList());
        assertThat(actualKeyValues, equalTo(expectedKeyValues));
    }

    @Test
    public void testProduceAvroMultipleEvents() {
        KafkaAvroSerializer avroSerializer
                = new KafkaAvroSerializer();
        avroSerializer.configure(commonConfigs, false);
        MockProducer<String, SpecificRecordBase> mockAvroProducer
                = new MockProducer<String, SpecificRecordBase>(true, stringSerializer, (Serializer) avroSerializer);
        produceConsumeApp.produceAvroEvents(() -> mockAvroProducer, (String) commonConfigs.get("proto.topic"), null);
        List<KeyValue<String, SpecificRecordBase>> expectedKeyValues =
                produceConsumeApp.avroEvents().stream().map((e -> KeyValue.pair((String) e.get("customer_id"), e))).collect(Collectors.toList());

        List<KeyValue<String, SpecificRecordBase>> actualKeyValues =
                mockAvroProducer.history().stream().map(this::toKeyValue).collect(Collectors.toList());
        assertThat(actualKeyValues, equalTo(expectedKeyValues));
    }

    @Test
    public void testConsumeProtobufEvents() {
        MockConsumer<String, CustomerEventProto.CustomerEvent> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        String topic = (String) commonConfigs.get("proto.topic");
        List<String> expectedProtoResults = List.of("http://acme/traps", "http://acme/bombs", "http://acme/bait", "road-runner-bait");
        List<String> actualProtoResults = new ArrayList<>();
        mockConsumer.schedulePollTask(()-> {
            addTopicPartitionsAssignment(topic, mockConsumer);
            addConsumerRecords(mockConsumer, produceConsumeApp.protobufEvents(), CustomerEventProto.CustomerEvent::getId, topic);
        });
        mockConsumer.schedulePollTask(() -> produceConsumeApp.close());
        produceConsumeApp.consumeProtoEvents(() -> mockConsumer, topic, actualProtoResults);
        assertThat(actualProtoResults, equalTo(expectedProtoResults));
    }

    @Test
    public void testConsumeAvroEvents() {
        MockConsumer<String, SpecificRecordBase> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        String topic = (String) commonConfigs.get("avro.topic");
        List<String> expectedAvroResults = List.of("http://acme/traps", "http://acme/bombs", "http://acme/bait", "road-runner-bait");
        List<String> actualAvroResults = new ArrayList<>();
        mockConsumer.schedulePollTask(() -> {
            addTopicPartitionsAssignment(topic, mockConsumer);
            addConsumerRecords(mockConsumer, produceConsumeApp.avroEvents(), (SpecificRecordBase r) -> (String) r.get("customer_id"), topic);
        });
        mockConsumer.schedulePollTask(() -> produceConsumeApp.close());
        produceConsumeApp.consumeAvroEvents(() -> mockConsumer, topic, actualAvroResults);
        assertThat(actualAvroResults, equalTo(expectedAvroResults));
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
