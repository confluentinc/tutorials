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

class AggregatingMinMaxTest {

    private static final MovieTicketSales SALES_ONE = new MovieTicketSales("Guardians of the Galaxy", 2020, 300_000_000);
    private static final MovieTicketSales SALES_TWO = new MovieTicketSales("Clockwork Orange", 2020, 100_000_000);
    private static final MovieTicketSales SALES_THREE = new MovieTicketSales("Spiderman", 2020, 200_000_000);
    private static final MovieTicketSales SALES_FOUR = new MovieTicketSales("Godfather", 1972, 84_000_000);
    private static final MovieTicketSales SALES_FIVE = new MovieTicketSales("The Poseidon Adventure", 1972, 42_000_000);
    private static final MovieTicketSales SALES_SIX = new MovieTicketSales("Cabaret", 1972, 22_000_000);

    private final AggregatingMinMax aggregatingMinMax = new AggregatingMinMax();
    private final Serde<MovieTicketSales> movieSalesSerde = StreamsSerde.serdeFor(MovieTicketSales.class);
    private final Serde<YearlyMovieFigures> yearlySalesSerde = StreamsSerde.serdeFor(YearlyMovieFigures.class);

    @Test
    @DisplayName("Should calculate min and max for movie sales in a given year")
    void calculateMinMaxTest() {

        Properties properties = new Properties();

        try (TopologyTestDriver driver = new TopologyTestDriver(aggregatingMinMax.buildTopology(properties))) {
            TestInputTopic<String, MovieTicketSales> inputTopic = driver.createInputTopic(AggregatingMinMax.INPUT_TOPIC,
                    Serdes.String().serializer(), movieSalesSerde.serializer());

            inputTopic.pipeValueList(List.of(SALES_SIX, SALES_ONE, SALES_FIVE, SALES_TWO, SALES_FOUR, SALES_THREE));

            long expectedMax2020Sales = 300_000_000;
            long expectedMin2020Sales = 100_000_000;
            long expectedMax1972Sales = 84_000_000;
            long expectedMin1972Sales = 22_000_000;

            TestOutputTopic<Integer, YearlyMovieFigures> outputTopic = driver.createOutputTopic(AggregatingMinMax.OUTPUT_TOPIC,
                    Serdes.Integer().deserializer(), yearlySalesSerde.deserializer());

            Map<Integer, YearlyMovieFigures> resultMap = outputTopic.readKeyValuesToMap();

            assertEquals(resultMap.get(2020).minTotalSales(), expectedMin2020Sales);
            assertEquals(resultMap.get(2020).maxTotalSales(), expectedMax2020Sales);
            assertEquals(resultMap.get(1972).minTotalSales(), expectedMin1972Sales);
            assertEquals(resultMap.get(1972).maxTotalSales(), expectedMax1972Sales);
        }
    }


}