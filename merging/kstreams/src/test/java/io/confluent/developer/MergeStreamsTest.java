package io.confluent.developer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
class MergeStreamsTest {
    
    @Test
    void testMergeStreams() {
        MergeStreams ms = new MergeStreams();
        Properties allProps = new Properties();


        try(TopologyTestDriver testDriver = new TopologyTestDriver(ms.buildTopology(allProps))) {

            Serializer<String> keySerializer = Serdes.String().serializer();
            Serde<SongEvent> songEventSerde = StreamsSerde.serdeFor(SongEvent.class);
            Serializer<SongEvent> valueSerializer = songEventSerde.serializer();
            Deserializer<String> keyDeserializer = Serdes.String().deserializer();
            Deserializer<SongEvent> valueDeserializer = songEventSerde.deserializer();

            String rockTopic = MergeStreams.ROCK_MUSIC_INPUT;
            String classicalTopic = MergeStreams.CLASSICAL_MUSIC_INPUT;
            String allGenreTopic = MergeStreams.ALL_MUSIC_OUTPUT;

            List<SongEvent> rockSongs = new ArrayList<>();
            List<SongEvent> classicalSongs = new ArrayList<>();

            rockSongs.add(new SongEvent("Metallica", "Fade to Black"));
            rockSongs.add(new SongEvent("Smashing Pumpkins", "Today"));
            rockSongs.add(new SongEvent("Pink Floyd", "Another Brick in the Wall"));
            rockSongs.add(new SongEvent("Van Halen", "Jump"));
            rockSongs.add(new SongEvent("Led Zeppelin", "Kashmir"));

            classicalSongs.add(new SongEvent("Wolfgang Amadeus Mozart", "The Magic Flute"));
            classicalSongs.add(new SongEvent("Johann Pachelbel", "Canon"));
            classicalSongs.add(new SongEvent("Ludwig van Beethoven", "Symphony No. 5"));
            classicalSongs.add(new SongEvent("Edward Elgar", "Pomp and Circumstance"));

            final TestInputTopic<String, SongEvent>
                    rockSongsTestDriverTopic =
                    testDriver.createInputTopic(rockTopic, keySerializer, valueSerializer);

            final TestInputTopic<String, SongEvent>
                    classicRockSongsTestDriverTopic =
                    testDriver.createInputTopic(classicalTopic, keySerializer, valueSerializer);

            for (SongEvent song : rockSongs) {
                rockSongsTestDriverTopic.pipeInput(song.artist(), song);
            }

            for (SongEvent song : classicalSongs) {
                classicRockSongsTestDriverTopic.pipeInput(song.artist(), song);
            }

            List<SongEvent> actualOutput =
                    testDriver
                            .createOutputTopic(allGenreTopic, keyDeserializer, valueDeserializer)
                            .readKeyValuesToList()
                            .stream()
                            .filter(record -> record.value != null)
                            .map(record -> record.value)
                            .collect(Collectors.toList());

            List<SongEvent> expectedOutput = new ArrayList<>();
            expectedOutput.addAll(rockSongs);
            expectedOutput.addAll(classicalSongs);

            assertEquals(expectedOutput, actualOutput);
        }
    }


}
