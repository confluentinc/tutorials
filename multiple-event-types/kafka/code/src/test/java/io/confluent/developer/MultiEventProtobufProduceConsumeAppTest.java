package io.confluent.developer;

import io.confluent.developer.proto.CustomerEventProto;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
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
import java.util.Arrays;
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

public class MultiEventProtobufProduceConsumeAppTest {
    private static final Map<String, Object> commonConfigs = new HashMap<>();
    private static final Properties properties = new Properties();
    private final Serializer<String> stringSerializer = new StringSerializer();
    private MultiEventProtobufProduceConsumeApp produceConsumeApp;

    @BeforeClass
    public static void beforeAllTests() throws IOException {
        try (FileInputStream fis = new FileInputStream("configuration/test.properties")) {
            properties.load(fis);
            properties.forEach((key, value) -> commonConfigs.put((String) key, value));
        }
    }


    @Before
    public void setup() {
        produceConsumeApp = new MultiEventProtobufProduceConsumeApp();
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
    public void testConsumeProtobufEvents() {
        MockConsumer<String, CustomerEventProto.CustomerEvent> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        String topic = (String) commonConfigs.get("proto.topic");
        List<String> expectedProtoResults = Arrays.asList("Protobuf Pageview event -> http://acme/traps", "Protobuf Pageview event -> http://acme/bombs", "Protobuf Pageview event -> http://acme/bait", "Protobuf Purchase event -> road-runner-bait");
        List<String> actualProtoResults = new ArrayList<>();
        mockConsumer.schedulePollTask(()-> {
            addTopicPartitionsAssignment(topic, mockConsumer);
            addConsumerRecords(mockConsumer, produceConsumeApp.protobufEvents(), CustomerEventProto.CustomerEvent::getId, topic);
        });
        mockConsumer.schedulePollTask(() -> produceConsumeApp.close());
        produceConsumeApp.consumeProtoEvents(() -> mockConsumer, topic, actualProtoResults);
        assertThat(actualProtoResults, equalTo(expectedProtoResults));
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
