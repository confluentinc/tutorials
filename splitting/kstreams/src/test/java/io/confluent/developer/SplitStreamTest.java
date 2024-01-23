package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.confluent.developer.SplitStream.INPUT_TOPIC;
import static io.confluent.developer.SplitStream.OUTPUT_TOPIC_DRAMA;
import static io.confluent.developer.SplitStream.OUTPUT_TOPIC_FANTASY;
import static io.confluent.developer.SplitStream.OUTPUT_TOPIC_OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SplitStreamTest {

    private final SplitStream splitStream = new SplitStream();


    private List<ActingEvent> readOutputTopic(TopologyTestDriver testDriver,
                                              String topic,
                                              Deserializer<String> keyDeserializer,
                                              Deserializer<ActingEvent> valueDeserializer) {

        return testDriver
                .createOutputTopic(topic, keyDeserializer, valueDeserializer)
                .readKeyValuesToList()
                .stream()
                .filter(Objects::nonNull)
                .map(record -> record.value)
                .collect(Collectors.toList());
    }

    @Test
    public void testSplitStream() throws IOException {
        Properties properties = new Properties();

        try (TopologyTestDriver testDriver = new TopologyTestDriver(splitStream.buildTopology(properties));
             Serde<ActingEvent> actingEventSerde = StreamsSerde.serdeFor(ActingEvent.class)) {

            Serializer<String> keySerializer = Serdes.String().serializer();
            Serializer<ActingEvent> valueSerializer = actingEventSerde.serializer();

            Deserializer<String> keyDeserializer = Serdes.String().deserializer();
            Deserializer<ActingEvent> valueDeserializer = actingEventSerde.deserializer();

            ActingEvent streep = new ActingEvent("Meryl Streep", "The Iron Lady", "drama");
            ActingEvent smith = new ActingEvent("Will Smith", "Men in Black", "comedy");
            ActingEvent damon = new ActingEvent("Matt Damon", "The Martian", "drama");
            ActingEvent garland = new ActingEvent("Judy Garland", "The Wizard of Oz", "fantasy");
            ActingEvent aniston = new ActingEvent("Jennifer Aniston", "Office Space", "comedy");
            ActingEvent murray = new ActingEvent("Bill Murray", "Ghostbusters", "fantasy");
            ActingEvent bale = new ActingEvent("Christian Bale", "The Dark Knight", "crime");
            ActingEvent dern = new ActingEvent("Laura Dern", "Jurassic Park", "fantasy");
            ActingEvent reeves = new ActingEvent("Keanu Reeves", "The Matrix", "fantasy");
            ActingEvent crowe = new ActingEvent("Russell Crowe", "Gladiator", "drama");
            ActingEvent keaton = new ActingEvent("Diane Keaton", "The Godfather: Part II", "crime");

            List<ActingEvent> input = new ArrayList<>();
            input.add(streep);
            input.add(smith);
            input.add(damon);
            input.add(garland);
            input.add(aniston);
            input.add(murray);
            input.add(bale);
            input.add(dern);
            input.add(reeves);
            input.add(crowe);
            input.add(keaton);

            List<ActingEvent> expectedDrama = new ArrayList<>();
            expectedDrama.add(streep);
            expectedDrama.add(damon);
            expectedDrama.add(crowe);

            List<ActingEvent> expectedFantasy = new ArrayList<>();
            expectedFantasy.add(garland);
            expectedFantasy.add(murray);
            expectedFantasy.add(dern);
            expectedFantasy.add(reeves);

            List<ActingEvent> expectedOther = new ArrayList<>();
            expectedOther.add(smith);
            expectedOther.add(aniston);
            expectedOther.add(bale);
            expectedOther.add(keaton);

            final TestInputTopic<String, ActingEvent>
                    actingEventTestInputTopic =
                    testDriver.createInputTopic(INPUT_TOPIC, keySerializer, valueSerializer);
            for (ActingEvent event : input) {
                actingEventTestInputTopic.pipeInput(event.name(), event);
            }

            List<ActingEvent> actualDrama = readOutputTopic(testDriver, OUTPUT_TOPIC_DRAMA, keyDeserializer, valueDeserializer);
            List<ActingEvent> actualFantasy = readOutputTopic(testDriver, OUTPUT_TOPIC_FANTASY, keyDeserializer, valueDeserializer);
            List<ActingEvent> actualOther = readOutputTopic(testDriver, OUTPUT_TOPIC_OTHER, keyDeserializer, valueDeserializer);

            assertEquals(expectedDrama, actualDrama);
            assertEquals(expectedFantasy, actualFantasy);
            assertEquals(expectedOther, actualOther);
        }
    }
}