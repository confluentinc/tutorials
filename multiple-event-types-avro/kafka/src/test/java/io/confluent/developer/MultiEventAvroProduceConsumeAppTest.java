package io.confluent.developer;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

import io.confluent.kafka.serializers.KafkaAvroSerializer;

import static io.confluent.developer.MultiEventAvroProduceConsumeApp.TOPIC;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class MultiEventAvroProduceConsumeAppTest {
    private static final Map<String, Object> commonConfigs = new HashMap<>();
    private final Serializer<String> stringSerializer = new StringSerializer();
    private MultiEventAvroProduceConsumeApp produceConsumeApp;

    @BeforeEach
    public void setup() {
        produceConsumeApp = new MultiEventAvroProduceConsumeApp();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProduceAvroMultipleEvents() {
        KafkaAvroSerializer avroSerializer
                = new KafkaAvroSerializer();
        commonConfigs.put("schema.registry.url", "mock://multi-event-produce-consume-test");
        avroSerializer.configure(commonConfigs, false);
        MockProducer<String, SpecificRecordBase> mockAvroProducer
                = new MockProducer<String, SpecificRecordBase>(true, stringSerializer, (Serializer) avroSerializer);
        produceConsumeApp.produceAvroEvents(() -> mockAvroProducer, TOPIC, produceConsumeApp.avroEvents());
        List<KeyValue<String, SpecificRecordBase>> expectedKeyValues =
                produceConsumeApp.avroEvents().stream().map((e -> KeyValue.pair((String) e.get("customer_id"), e))).collect(Collectors.toList());

        List<KeyValue<String, SpecificRecordBase>> actualKeyValues =
                mockAvroProducer.history().stream().map(this::toKeyValue).collect(Collectors.toList());
        assertThat(actualKeyValues, equalTo(expectedKeyValues));
    }

    @Test
    public void testConsumeAvroEvents() {
        MockConsumer<String, SpecificRecordBase> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        List<String> expectedAvroResults = Arrays.asList("Avro Pageview event -> http://acme/traps", "Avro Pageview event -> http://acme/bombs", "Avro Pageview event -> http://acme/bait", "Avro Purchase event -> road-runner-bait");
        List<String> actualAvroResults = new ArrayList<>();
        mockConsumer.schedulePollTask(() -> {
            addTopicPartitionsAssignment(TOPIC, mockConsumer);
            addConsumerRecords(mockConsumer, produceConsumeApp.avroEvents(), (SpecificRecordBase r) -> (String) r.get("customer_id"), TOPIC);
        });
        mockConsumer.schedulePollTask(() -> produceConsumeApp.close());
        produceConsumeApp.consumeAvroEvents(() -> mockConsumer, TOPIC, actualAvroResults);
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
