package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import io.confluent.developer.avro.SongEvent;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;

public class MergeStreamsTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";
    private TopologyTestDriver testDriver;

    public SpecificAvroSerializer<SongEvent> makeSerializer(Properties allProps) {
        SpecificAvroSerializer<SongEvent> serializer = new SpecificAvroSerializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        serializer.configure(config, false);

        return serializer;
    }

    public SpecificAvroDeserializer<SongEvent> makeDeserializer(Properties allProps) {
        SpecificAvroDeserializer<SongEvent> deserializer = new SpecificAvroDeserializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        deserializer.configure(config, false);

        return deserializer;
    }

    @Test
    public void testMergeStreams() throws IOException {
        MergeStreams ms = new MergeStreams();
        Properties allProps = ms.loadEnvProperties(TEST_CONFIG_FILE);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        String rockTopic = allProps.getProperty("input.rock.topic.name");
        String classicalTopic = allProps.getProperty("input.classical.topic.name");
        String allGenresTopic = allProps.getProperty("output.topic.name");

        Topology topology = ms.buildTopology(allProps);
        testDriver = new TopologyTestDriver(topology, allProps);

        Serializer<String> keySerializer = Serdes.String().serializer();
        SpecificAvroSerializer<SongEvent> valueSerializer = makeSerializer(allProps);

        Deserializer<String> keyDeserializer = Serdes.String().deserializer();
        SpecificAvroDeserializer<SongEvent> valueDeserializer = makeDeserializer(allProps);

        List<SongEvent> rockSongs = new ArrayList<>();
        List<SongEvent> classicalSongs = new ArrayList<>();

        rockSongs.add(SongEvent.newBuilder().setArtist("Metallica").setTitle("Fade to Black").build());
        rockSongs.add(SongEvent.newBuilder().setArtist("Smashing Pumpkins").setTitle("Today").build());
        rockSongs.add(SongEvent.newBuilder().setArtist("Pink Floyd").setTitle("Another Brick in the Wall").build());
        rockSongs.add(SongEvent.newBuilder().setArtist("Van Halen").setTitle("Jump").build());
        rockSongs.add(SongEvent.newBuilder().setArtist("Led Zeppelin").setTitle("Kashmir").build());

        classicalSongs.add(SongEvent.newBuilder().setArtist("Wolfgang Amadeus Mozart").setTitle("The Magic Flute").build());
        classicalSongs.add(SongEvent.newBuilder().setArtist("Johann Pachelbel").setTitle("Canon").build());
        classicalSongs.add(SongEvent.newBuilder().setArtist("Ludwig van Beethoven").setTitle("Symphony No. 5").build());
        classicalSongs.add(SongEvent.newBuilder().setArtist("Edward Elgar").setTitle("Pomp and Circumstance").build());

        final TestInputTopic<String, SongEvent>
            rockSongsTestDriverTopic =
            testDriver.createInputTopic(rockTopic, keySerializer, valueSerializer);

        final TestInputTopic<String, SongEvent>
            classicRockSongsTestDriverTopic =
            testDriver.createInputTopic(classicalTopic, keySerializer, valueSerializer);

        for (SongEvent song : rockSongs) {
            rockSongsTestDriverTopic.pipeInput(song.getArtist(), song);
        }

        for (SongEvent song : classicalSongs) {
            classicRockSongsTestDriverTopic.pipeInput(song.getArtist(), song);
        }

        List<SongEvent> actualOutput =
            testDriver
                .createOutputTopic(allGenresTopic, keyDeserializer, valueDeserializer)
                .readKeyValuesToList()
                .stream()
                .filter(record -> record.value != null)
                .map(record -> record.value)
                .collect(Collectors.toList());
        
        List<SongEvent> expectedOutput = new ArrayList<>();
        expectedOutput.addAll(rockSongs);
        expectedOutput.addAll(classicalSongs);
        
        Assert.assertEquals(expectedOutput, actualOutput);
    }

    @After
    public void cleanup() {
        testDriver.close();
    }
}
