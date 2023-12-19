package io.confluent.developer;


import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;


public class DynamicOutputTopic {

    static final double FAKE_PRICE = 0.467423D;
    static final String INPUT_TOPIC = "dynamic-topic-input";
    static final String OUTPUT_TOPIC = "dynamic-topic-output";
    static final String SPECIAL_ORDER_OUTPUT_TOPIC = "special-order-output";
     private static final Logger LOG = LoggerFactory.getLogger(DynamicOutputTopic.class);
    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        final Serde<String> stringSerde = Serdes.String();
        final Serde<Order> orderSerde = StreamsSerde.serdeFor(Order.class);
        final Serde<CompletedOrder> completedOrderSerde = StreamsSerde.serdeFor(CompletedOrder.class);

        final ValueMapper<Order, CompletedOrder> orderProcessingSimulator = v -> {
           double amount = v.quantity() * FAKE_PRICE;
           return new CompletedOrder(v.id()+"-"+v.sku(), v.name(), amount);
        };

        final TopicNameExtractor<String, CompletedOrder> orderTopicNameExtractor = (key, completedOrder, recordContext) -> {
              final String compositeId = completedOrder.id();
              final String skuPart = compositeId.substring(compositeId.indexOf('-') + 1, 5);
              final String outTopic;
              if (skuPart.equals("QUA")) {
                  outTopic = SPECIAL_ORDER_OUTPUT_TOPIC;
              } else {
                  outTopic = OUTPUT_TOPIC;
              }
              return outTopic;
        };

         builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, orderSerde))
                 .peek((key, value) -> LOG.info("key[{}] value[{}]", key, value))
         .mapValues(orderProcessingSimulator)
                 .to(orderTopicNameExtractor, Produced.with(stringSerde, completedOrderSerde));

        return builder.build(allProps);
    }


    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "dynamic-output-topic");
        DynamicOutputTopic dynamicOutputTopic = new DynamicOutputTopic();

        Topology topology = dynamicOutputTopic.buildTopology(properties);

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only don't do this in production as it wipes out all local state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
