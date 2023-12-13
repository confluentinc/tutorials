package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AggregatingSumTest {


    private static final TicketSale TICKET_SALE = new TicketSale("Guardians of the Galaxy", 15);
    private static final TicketSale TICKET_SALE_II = new TicketSale("Doctor Strange", 15);
    private static final TicketSale TICKET_SALE_III = new TicketSale("Guardians of the Galaxy", 15);

    private final AggregatingSum aggregatingSum = new AggregatingSum();
    @Test
    @DisplayName("Summing all values by key")
    void shouldSumValuesByKeyTest() {
        Properties properties = new Properties();
        Serde<TicketSale> ticketSaleSerde = StreamsSerde.serdeFor(TicketSale.class);
        try(TopologyTestDriver driver = new TopologyTestDriver(aggregatingSum.buildTopology(properties))) {
            TestInputTopic<String, TicketSale> inputTopic = driver.createInputTopic(AggregatingSum.INPUT_TOPIC,
                    Serdes.String().serializer(), ticketSaleSerde.serializer());

            TestOutputTopic<String, String> outputTopic = driver.createOutputTopic(AggregatingSum.OUTPUT_TOPIC,
                    Serdes.String().deserializer(), Serdes.String().deserializer());

            inputTopic.pipeValueList(List.of(TICKET_SALE, TICKET_SALE_II, TICKET_SALE_III));

            String expectedValueGuardians = "30 total sales";
            String expectedValueDrStrange = "15 total sales";

            Map<String, String> results = outputTopic.readKeyValuesToMap();
            assertEquals(results.get("Guardians of the Galaxy"), expectedValueGuardians);
            assertEquals(results.get("Doctor Strange"), expectedValueDrStrange);
        }
    }
}