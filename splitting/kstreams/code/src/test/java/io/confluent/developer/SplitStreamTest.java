package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import io.confluent.developer.avro.ActingEvent;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

public class SplitStreamTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";
    private TopologyTestDriver testDriver;

    public SpecificAvroSerializer<ActingEvent> makeSerializer(Properties allProps) {
        SpecificAvroSerializer<ActingEvent> serializer = new SpecificAvroSerializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        serializer.configure(config, false);

        return serializer;
    }

    public SpecificAvroDeserializer<ActingEvent> makeDeserializer(Properties allProps) {
        SpecificAvroDeserializer<ActingEvent> deserializer = new SpecificAvroDeserializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        deserializer.configure(config, false);

        return deserializer;
    }

    private List<ActingEvent> readOutputTopic(TopologyTestDriver testDriver,
                                              String topic,
                                              Deserializer<String> keyDeserializer,
                                              SpecificAvroDeserializer<ActingEvent> valueDeserializer) {

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
        SplitStream ss = new SplitStream();
        Properties allProps = ss.loadEnvProperties(TEST_CONFIG_FILE);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);

        String inputTopic = allProps.getProperty("input.topic.name");
        String outputDramaTopic = allProps.getProperty("output.drama.topic.name");
        String outputFantasyTopic = allProps.getProperty("output.fantasy.topic.name");
        String outputOtherTopic = allProps.getProperty("output.other.topic.name");

        Topology topology = ss.buildTopology(allProps);
        testDriver = new TopologyTestDriver(topology, allProps);

        Serializer<String> keySerializer = Serdes.String().serializer();
        SpecificAvroSerializer<ActingEvent> valueSerializer = makeSerializer(allProps);

        Deserializer<String> keyDeserializer = Serdes.String().deserializer();
        SpecificAvroDeserializer<ActingEvent> valueDeserializer = makeDeserializer(allProps);

        ActingEvent streep = ActingEvent.newBuilder()
                .setName("Meryl Streep").setTitle("The Iron Lady").setGenre("drama").build();
        ActingEvent smith = ActingEvent.newBuilder()
                .setName("Will Smith").setTitle("Men in Black").setGenre("comedy").build();
        ActingEvent damon = ActingEvent.newBuilder()
                .setName("Matt Damon").setTitle("The Martian").setGenre("drama").build();
        ActingEvent garland = ActingEvent.newBuilder()
                .setName("Judy Garland").setTitle("The Wizard of Oz").setGenre("fantasy").build();
        ActingEvent aniston = ActingEvent.newBuilder()
                .setName("Jennifer Aniston").setTitle("Office Space").setGenre("comedy").build();
        ActingEvent murray = ActingEvent.newBuilder()
                .setName("Bill Murray").setTitle("Ghostbusters").setGenre("fantasy").build();
        ActingEvent bale = ActingEvent.newBuilder()
                .setName("Christian Bale").setTitle("The Dark Knight").setGenre("crime").build();
        ActingEvent dern = ActingEvent.newBuilder()
                .setName("Laura Dern").setTitle("Jurassic Park").setGenre("fantasy").build();
        ActingEvent reeves = ActingEvent.newBuilder()
                .setName("Keanu Reeves").setTitle("The Matrix").setGenre("fantasy").build();
        ActingEvent crowe = ActingEvent.newBuilder()
                .setName("Russell Crowe").setTitle("Gladiator").setGenre("drama").build();
        ActingEvent keaton = ActingEvent.newBuilder()
                .setName("Diane Keaton").setTitle("The Godfather: Part II").setGenre("crime").build();

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
            testDriver.createInputTopic(inputTopic, keySerializer, valueSerializer);
        for (ActingEvent event : input) {
            actingEventTestInputTopic.pipeInput(event.getName(), event);
        }

        List<ActingEvent> actualDrama = readOutputTopic(testDriver, outputDramaTopic, keyDeserializer, valueDeserializer);
        List<ActingEvent> actualFantasy = readOutputTopic(testDriver, outputFantasyTopic, keyDeserializer, valueDeserializer);
        List<ActingEvent> actualOther = readOutputTopic(testDriver, outputOtherTopic, keyDeserializer, valueDeserializer);

        Assert.assertEquals(expectedDrama, actualDrama);
        Assert.assertEquals(expectedFantasy, actualFantasy);
        Assert.assertEquals(expectedOther, actualOther);
    }

    @After
    public void cleanup() {
        testDriver.close();
    }
}