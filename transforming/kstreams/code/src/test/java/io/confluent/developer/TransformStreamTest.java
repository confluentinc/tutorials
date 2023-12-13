package io.confluent.developer;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.avro.RawMovie;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TransformStreamTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";
    private TopologyTestDriver testDriver;

    public SpecificAvroSerializer<RawMovie> makeSerializer(Properties allProps) {
        SpecificAvroSerializer<RawMovie> serializer = new SpecificAvroSerializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        serializer.configure(config, false);

        return serializer;
    }

    public SpecificAvroDeserializer<Movie> makeDeserializer(Properties allProps) {
        SpecificAvroDeserializer<Movie> deserializer = new SpecificAvroDeserializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", allProps.getProperty("schema.registry.url"));
        deserializer.configure(config, false);

        return deserializer;
    }

    private List<Movie> readOutputTopic(TopologyTestDriver testDriver,
                                        String topic,
                                        Deserializer<String> keyDeserializer,
                                        SpecificAvroDeserializer<Movie> valueDeserializer) {

        return testDriver
            .createOutputTopic(topic, keyDeserializer, valueDeserializer)
            .readKeyValuesToList()
            .stream()
            .filter(Objects::nonNull)
            .map(record -> record.value)
            .collect(Collectors.toList());
    }

    @Test
    public void testMovieConverter() {
        Movie movie;

        movie = TransformStream.convertRawMovie(new RawMovie(294L, "Tree of Life::2011", "drama"));
        assertNotNull(movie);
        assertEquals(294L, movie.getId());
        assertEquals("Tree of Life", movie.getTitle());
        assertEquals(2011, movie.getReleaseYear());
        assertEquals("drama", movie.getGenre());
    }


    @Test
    public void testTransformStream() throws IOException {
        TransformStream ts = new TransformStream();
        Properties allProps = ts.loadEnvProperties(TEST_CONFIG_FILE);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);

        String inputTopic = allProps.getProperty("input.topic.name");
        String outputTopic = allProps.getProperty("output.topic.name");

        Topology topology = ts.buildTopology(allProps);
        testDriver = new TopologyTestDriver(topology, allProps);

        Serializer<String> keySerializer = Serdes.String().serializer();
        SpecificAvroSerializer<RawMovie> valueSerializer = makeSerializer(allProps);

        Deserializer<String> keyDeserializer = Serdes.String().deserializer();
        SpecificAvroDeserializer<Movie> valueDeserializer = makeDeserializer(allProps);

        List<RawMovie> input = new ArrayList<>();
        input.add(RawMovie.newBuilder().setId(294).setTitle("Die Hard::1988").setGenre("action").build());
        input.add(RawMovie.newBuilder().setId(354).setTitle("Tree of Life::2011").setGenre("drama").build());
        input.add(RawMovie.newBuilder().setId(782).setTitle("A Walk in the Clouds::1995").setGenre("romance").build());
        input.add(RawMovie.newBuilder().setId(128).setTitle("The Big Lebowski::1998").setGenre("comedy").build());

        List<Movie> expectedOutput = new ArrayList<>();
        expectedOutput.add(Movie.newBuilder().setTitle("Die Hard").setId(294).setReleaseYear(1988).setGenre("action").build());
        expectedOutput.add(Movie.newBuilder().setTitle("Tree of Life").setId(354).setReleaseYear(2011).setGenre("drama").build());
        expectedOutput.add(Movie.newBuilder().setTitle("A Walk in the Clouds").setId(782).setReleaseYear(1995).setGenre("romance").build());
        expectedOutput.add(Movie.newBuilder().setTitle("The Big Lebowski").setId(128).setReleaseYear(1998).setGenre("comedy").build());

        final TestInputTopic<String, RawMovie>
            testDriverInputTopic =
            testDriver.createInputTopic(inputTopic, keySerializer, valueSerializer);
        
        for (RawMovie rawMovie : input) {
            testDriverInputTopic.pipeInput(rawMovie.getTitle(), rawMovie);
        }
        List<Movie> actualOutput = readOutputTopic(testDriver, outputTopic, keyDeserializer, valueDeserializer);

        assertEquals(expectedOutput, actualOutput);
    }

    @After
    public void cleanup() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

}
