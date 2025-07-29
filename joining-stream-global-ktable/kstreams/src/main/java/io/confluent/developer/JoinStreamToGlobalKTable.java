package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class JoinStreamToGlobalKTable {

    public static final String PRODUCT_INPUT_TOPIC = "product-input";
    public static final String ORDERS_INPUT_TOPIC = "orders-input";
    public static final String ENRICHED_ORDERS_OUTPUT = "enriched-orders-output";

    public Topology buildTopology(Properties allProps) {

        final StreamsBuilder builder = new StreamsBuilder();
        final Serde<Product> productSerde = StreamsSerde.serdeFor(Product.class);
        final Serde<Order> orderSerde = StreamsSerde.serdeFor(Order.class);
        final Serde<EnrichedOrder> enrichedOrderSerde = StreamsSerde.serdeFor(EnrichedOrder.class);

        GlobalKTable<String, Product> products = builder.globalTable(PRODUCT_INPUT_TOPIC,
                Consumed.with(Serdes.String(), productSerde));

        KStream<String, Order> orders = builder.stream(ORDERS_INPUT_TOPIC,
                Consumed.with(Serdes.String(), orderSerde));

        // Join the orders stream to the products global table. As this is a global
        // table we can use a non-key based join without needing to repartition the
        // input stream
        orders.join(products,
                (orderId, order) -> order.productId(),
                (order, product) -> new EnrichedOrder(order.orderId(), product.productId(), product.productName()))
                .to(ENRICHED_ORDERS_OUTPUT, Produced.with(Serdes.String(), enrichedOrderSerde));

        return builder.build(allProps);
    }

    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "join-stream-to-global-table");
        JoinStreamToGlobalKTable joinStreamToGlobalKTable = new JoinStreamToGlobalKTable();

        Topology topology = joinStreamToGlobalKTable.buildTopology(properties);

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only don't do this in production as it wipes out all local
            // state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
