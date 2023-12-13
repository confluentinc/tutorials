package io.confluent.developer;


import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.apache.kafka.streams.processor.TopicNameExtractor;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.common.utils.TestUtils;
import io.confluent.developer.avro.CompletedOrder;
import io.confluent.developer.avro.Order;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

public class DynamicOutputTopic {

    static final double FAKE_PRICE = 0.467423D;

    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String orderInputTopic = allProps.getProperty("input.topic.name");
        final String orderOutputTopic = allProps.getProperty("output.topic.name");
        final String specialOrderOutput = allProps.getProperty("special.order.topic.name");

        final Serde<String> stringSerde = Serdes.String();
        final Serde<Order> orderSerde = getSpecificAvroSerde(allProps);
        final Serde<CompletedOrder> completedOrderSerde = getSpecificAvroSerde(allProps);

        final ValueMapper<Order, CompletedOrder> orderProcessingSimulator = v -> {
           double amount = v.getQuantity() * FAKE_PRICE;
           return CompletedOrder.newBuilder().setAmount(amount).setId(v.getId() + "-" + v.getSku()).setName(v.getName()).build();
        };

        final TopicNameExtractor<String, CompletedOrder> orderTopicNameExtractor = (key, completedOrder, recordContext) -> {
              final String compositeId = completedOrder.getId();
              final String skuPart = compositeId.substring(compositeId.indexOf('-') + 1, 5);
              final String outTopic;
              if (skuPart.equals("QUA")) {
                  outTopic = specialOrderOutput;
              } else {
                  outTopic = orderOutputTopic;
              }
              return outTopic;
        };

        final KStream<String, Order> exampleStream = builder.stream(orderInputTopic, Consumed.with(stringSerde, orderSerde));

        exampleStream.mapValues(orderProcessingSimulator).to(orderTopicNameExtractor, Produced.with(stringSerde, completedOrderSerde));

        return builder.build();
    }

    static <T extends SpecificRecord> SpecificAvroSerde<T> getSpecificAvroSerde(final Properties allProps) {
        final SpecificAvroSerde<T> specificAvroSerde = new SpecificAvroSerde<>();

        final Map<String, String> serdeConfig = (Map)allProps;
        specificAvroSerde.configure(serdeConfig, false);
        return specificAvroSerde;
    }

    public void createTopics(final Properties allProps) {
        try (final AdminClient client = AdminClient.create(allProps)) {

        final List<NewTopic> topics = new ArrayList<>();

            topics.add(new NewTopic(
                    allProps.getProperty("input.topic.name"),
                    Integer.parseInt(allProps.getProperty("input.topic.partitions")),
                    Short.parseShort(allProps.getProperty("input.topic.replication.factor"))));

            topics.add(new NewTopic(
                    allProps.getProperty("output.topic.name"),
                    Integer.parseInt(allProps.getProperty("output.topic.partitions")),
                    Short.parseShort(allProps.getProperty("output.topic.replication.factor"))));

            topics.add(new NewTopic(
                allProps.getProperty("special.order.topic.name"),
                Integer.parseInt(allProps.getProperty("special.order.topic.partitions")),
                Short.parseShort(allProps.getProperty("special.order.topic.replication.factor"))));

            client.createTopics(topics);
        }
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        final Properties allProps = new Properties();
        final FileInputStream input = new FileInputStream(fileName);
        allProps.load(input);
        input.close();

        return allProps;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        final DynamicOutputTopic instance = new DynamicOutputTopic();

        final Properties allProps = new Properties();
        try (InputStream inputStream = new FileInputStream(args[0])) {
            allProps.load(inputStream);
        }
        allProps.put(StreamsConfig.APPLICATION_ID_CONFIG, allProps.getProperty("application.id"));
        allProps.put(StreamsConfig.STATE_DIR_CONFIG, TestUtils.tempDirectory().getPath());

        final Topology topology = instance.buildTopology(allProps);

        instance.createTopics(allProps);

        final KafkaStreams streams = new KafkaStreams(topology, allProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close(Duration.ofSeconds(5));
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

}
