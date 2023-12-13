package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static io.confluent.developer.KafkaStreamsAggregatingCount.INPUT_TOPIC;
import static io.confluent.developer.KafkaStreamsAggregatingCount.OUTPUT_TOPIC;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;


class KafkaStreamsAggregatingCountTest {

    private static final TicketSale TICKET_SALE = new TicketSale("Guardians of the Galaxy", 15);
    private static final TicketSale TICKET_SALE_II = new TicketSale("Doctor Strange", 15);
    private static final TicketSale TICKET_SALE_III = new TicketSale("Guardians of the Galaxy", 15);

    KafkaStreamsAggregatingCount aggregatingCount = new KafkaStreamsAggregatingCount();


    @Test
    @DisplayName("Should count total number of ticket sales")
    void countNumberSalesTest() {
        Properties properties = new Properties();

        try (TopologyTestDriver testDriver = new TopologyTestDriver(aggregatingCount.buildTopology(properties));
             Serde<TicketSale> ticketSaleSerde = StreamsSerde.serdeFor(TicketSale.class)) {

            TestInputTopic<String, TicketSale> inputTopic = testDriver.createInputTopic(INPUT_TOPIC,
                    new StringSerializer(),
                    ticketSaleSerde.serializer());

            inputTopic.pipeValueList(asList(TICKET_SALE, TICKET_SALE_II, TICKET_SALE_III));

            final TestOutputTopic<String, String> outputTopic = testDriver.createOutputTopic(OUTPUT_TOPIC,
                    new StringDeserializer(),
                    new StringDeserializer());
            
            String expectedGuardiansOutput_2 = "2 tickets sold";
            String expectedDrStrangeOutput = "1 tickets sold";

            final Map<String, String> actualKeyValuesMap = outputTopic.readKeyValuesToMap();
            assertThat(actualKeyValuesMap.get("Guardians of the Galaxy"), equalTo(expectedGuardiansOutput_2));
            assertThat(actualKeyValuesMap.get("Doctor Strange"), equalTo(expectedDrStrangeOutput));
        }
    }
}