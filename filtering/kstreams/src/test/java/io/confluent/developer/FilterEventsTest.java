package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterEventsTest {

    @Test
    @DisplayName("Should filter out books not written by George R. R. Martin")
    void shouldFilterGRRMartinsBooks() {
        FilterEvents filterEvents = new FilterEvents();


        String inputTopic = FilterEvents.INPUT_TOPIC;
        String outputTopic = FilterEvents.OUTPUT_TOPIC;

        final Serde<Publication> publicationSerde = StreamsSerde.serdeFor(Publication.class);
        Properties properties = new Properties();
        Topology topology = filterEvents.buildTopology(properties);
        try (TopologyTestDriver testDriver = new TopologyTestDriver(topology, properties)) {

            Serializer<String> keySerializer = Serdes.String().serializer();
            Deserializer<String> keyDeserializer = Serdes.String().deserializer();


            Publication iceAndFire = new Publication("George R. R. Martin", "A Song of Ice and Fire");
            Publication silverChair = new Publication("C.S. Lewis", "The Silver Chair");
            Publication perelandra = new Publication("C.S. Lewis", "Perelandra");
            Publication fireAndBlood = new Publication("George R. R. Martin", "Fire & Blood");
            Publication theHobbit = new Publication("J. R. R. Tolkien", "The Hobbit");
            Publication lotr = new Publication("J. R. R. Tolkien", "The Lord of the Rings");
            Publication dreamOfSpring = new Publication("George R. R. Martin", "A Dream of Spring");
            Publication fellowship = new Publication("J. R. R. Tolkien", "The Fellowship of the Ring");
            Publication iceDragon = new Publication("George R. R. Martin", "The Ice Dragon");

            final List<Publication>
                    input = asList(iceAndFire, silverChair, perelandra, fireAndBlood, theHobbit, lotr, dreamOfSpring, fellowship,
                    iceDragon);

            final List<Publication> expectedOutput = asList(iceAndFire, fireAndBlood, dreamOfSpring, iceDragon);

            testDriver.createInputTopic(inputTopic, keySerializer, publicationSerde.serializer())
                    .pipeValueList(input);

            List<Publication> actualOutput =
                    testDriver
                            .createOutputTopic(outputTopic, keyDeserializer, publicationSerde.deserializer())
                            .readValuesToList()
                            .stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

            assertEquals(expectedOutput, actualOutput);
        }
    }

}
