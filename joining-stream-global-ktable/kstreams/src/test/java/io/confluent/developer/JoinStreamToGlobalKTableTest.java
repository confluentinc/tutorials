package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinStreamToGlobalKTableTest {

    @Test
    @DisplayName("Should join product table with orders stream")
    void testJoin() {
        JoinStreamToGlobalKTable jst = new JoinStreamToGlobalKTable();
        Properties allProps = new Properties();

        String tableTopic = JoinStreamToGlobalKTable.PRODUCT_INPUT_TOPIC;
        String streamTopic = JoinStreamToGlobalKTable.ORDERS_INPUT_TOPIC;
        String outputTopic = JoinStreamToGlobalKTable.ENRICHED_ORDERS_OUTPUT;
        Topology topology = jst.buildTopology(allProps);
        try (TopologyTestDriver driver = new TopologyTestDriver(topology, allProps)) {

            Serde<Product> productSerde = StreamsSerde.serdeFor(Product.class);
            Serde<Order> orderSerde = StreamsSerde.serdeFor(Order.class);
            Serde<EnrichedOrder> enrichedOrderSerde = StreamsSerde.serdeFor(EnrichedOrder.class);

            Serializer<String> keySerializer = Serdes.String().serializer();
            Serializer<Product> productSerializer = productSerde.serializer();
            Serializer<Order> orderSerializer = orderSerde.serializer();
            Deserializer<String> keyDeserializer = Serdes.String().deserializer();
            Deserializer<EnrichedOrder> valueDeserializer = enrichedOrderSerde.deserializer();

            List<Product> products = getProducts();
            List<Order> orders = getOrders();
            List<EnrichedOrder> expectedEnrichedOrders = getEnrichedOrders();

            final TestInputTopic<String, Product> productTestInputTopic = driver.createInputTopic(tableTopic,
                    keySerializer, productSerializer);
            final TestInputTopic<String, Order> orderTestInputTopic = driver.createInputTopic(streamTopic,
                    keySerializer, orderSerializer);
            final TestOutputTopic<String, EnrichedOrder> enrichedOrderTestOutputTopic = driver
                    .createOutputTopic(outputTopic, keyDeserializer, valueDeserializer);

            for (Product product : products) {
                productTestInputTopic.pipeInput(product.productId(), product);
            }
            for (Order order : orders) {
                orderTestInputTopic.pipeInput(order.orderId(), order);
            }

            List<EnrichedOrder> actualOutput = enrichedOrderTestOutputTopic.readValuesToList();

            assertEquals(expectedEnrichedOrders, actualOutput);
        }
    }

    private static List<Product> getProducts() {
        List<Product> products = new ArrayList<>();
        products.add(new Product("1", "sneakers"));
        products.add(new Product("2", "basketball"));
        products.add(new Product("3", "baseball"));
        products.add(new Product("4", "sweatshirt"));
        products.add(new Product("5", "tv"));
        return products;
    }

    private static List<Order> getOrders() {
        List<Order> orders = new ArrayList<>();
        orders.add(new Order("1", "1"));
        orders.add(new Order("2", "3"));
        orders.add(new Order("3", "2"));
        orders.add(new Order("4", "5"));
        orders.add(new Order("5", "2"));
        orders.add(new Order("6", "1"));
        orders.add(new Order("7", "5"));
        orders.add(new Order("8", "4"));
        orders.add(new Order("9", "3"));
        return orders;
    }

    private static List<EnrichedOrder> getEnrichedOrders() {
        List<EnrichedOrder> expectedEnrichedOrders = new ArrayList<>();
        expectedEnrichedOrders.add(new EnrichedOrder("1", "1", "sneakers"));
        expectedEnrichedOrders.add(new EnrichedOrder("2", "3", "baseball"));
        expectedEnrichedOrders.add(new EnrichedOrder("3", "2", "basketball"));
        expectedEnrichedOrders.add(new EnrichedOrder("4", "5", "tv"));
        expectedEnrichedOrders.add(new EnrichedOrder("5", "2", "basketball"));
        expectedEnrichedOrders.add(new EnrichedOrder("6", "1", "sneakers"));
        expectedEnrichedOrders.add(new EnrichedOrder("7", "5", "tv"));
        expectedEnrichedOrders.add(new EnrichedOrder("8", "4", "sweatshirt"));
        expectedEnrichedOrders.add(new EnrichedOrder("9", "3", "baseball"));
        return expectedEnrichedOrders;
    }
}
