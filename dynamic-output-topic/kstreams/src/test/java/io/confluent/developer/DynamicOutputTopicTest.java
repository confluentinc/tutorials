package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class DynamicOutputTopicTest {


    @Test
    @DisplayName("Picks Topic Dynamically from record contents")
    void shouldChooseTopic() {
        Properties properties = new Properties();
        DynamicOutputTopic dynamicOutputTopic = new DynamicOutputTopic();
        Topology topology = dynamicOutputTopic.buildTopology(properties);
        try (TopologyTestDriver driver = new TopologyTestDriver(topology)) {
            Serde<Order> orderSerde = StreamsSerde.serdeFor(Order.class);
            Serde<CompletedOrder> completedOrderSerde = StreamsSerde.serdeFor(CompletedOrder.class);
            Serde<String> stringSerde = Serdes.String();
            TestInputTopic<String, Order> inputTopic = driver.createInputTopic(DynamicOutputTopic.INPUT_TOPIC, stringSerde.serializer(), orderSerde.serializer());
            TestOutputTopic<String, CompletedOrder> regularOutputTopic = driver.createOutputTopic(DynamicOutputTopic.OUTPUT_TOPIC, stringSerde.deserializer(), completedOrderSerde.deserializer());
            TestOutputTopic<String, CompletedOrder> specialOutputTopic = driver.createOutputTopic(DynamicOutputTopic.SPECIAL_ORDER_OUTPUT_TOPIC, stringSerde.deserializer(), completedOrderSerde.deserializer());

            final List<Order> orders = List.of(new Order(5L,"QUA00000123","tp",10_000L),
                    new Order(6L,"COF0003456", "coffee",1_000L),
                    new Order(7L,"QUA000022334", "hand-sanitizer",6_000L),
                    new Order(8L,"BER88899222", "beer",4_000L)
            );

            final List<CompletedOrder> expectedRegularCompletedOrders = List.of(
                    new CompletedOrder( "6-COF0003456","coffee",  1_000L * DynamicOutputTopic.FAKE_PRICE),
                    new CompletedOrder( "8-BER88899222","beer",  4_000L * DynamicOutputTopic.FAKE_PRICE)
            );

            final List<CompletedOrder> expectedSpecialOrders = List.of(
                    new CompletedOrder("5-QUA00000123","tp",  10_000L * DynamicOutputTopic.FAKE_PRICE),
                    new CompletedOrder("7-QUA000022334","hand-sanitizer",  6_000L * DynamicOutputTopic.FAKE_PRICE)
            );

            for (final Order order : orders) {
                inputTopic.pipeInput(String.valueOf(order.id()), order);
            }

            final List<CompletedOrder> actualRegularOrderResults = regularOutputTopic.readValuesToList();
            final List<CompletedOrder> actualSpecialCompletedOrders = specialOutputTopic.readValuesToList();

            assertThat(expectedRegularCompletedOrders, equalTo(actualRegularOrderResults));
            assertThat(expectedSpecialOrders, equalTo(actualSpecialCompletedOrders));

        }


    }


}