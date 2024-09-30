package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import static io.confluent.developer.WindowFinalResult.INPUT_TOPIC;
import static io.confluent.developer.WindowFinalResult.OUTPUT_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PressureDatetimeExtractorTest {

    private TopologyTestDriver testDriver;
    private Serde<PressureAlert> pressureSerde;

    private final String inputTopic = INPUT_TOPIC;
    private final String outputTopic = OUTPUT_TOPIC;

    private final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss.Z")
        .toFormatter();

    private final PressureDatetimeExtractor timestampExtractor = new PressureDatetimeExtractor();
    private TestOutputTopic<String, PressureAlert> testDriverOutputTopic;


    private List<TestRecord<String, PressureAlert>> readNOutputs(int size) {
        return testDriverOutputTopic.readRecordsToList();
    }

    @BeforeEach
    public void setUp() {
        this.pressureSerde = StreamsSerde.serdeFor(PressureAlert.class);

        Consumed<String, PressureAlert> consumedPressure =
            Consumed.with(Serdes.String(), pressureSerde)
                .withTimestampExtractor(timestampExtractor);

        Produced<String, PressureAlert> producedPressure =
            Produced.with(Serdes.String(), pressureSerde);

        StreamsBuilder builder = new StreamsBuilder();
        builder.stream(this.inputTopic, consumedPressure).to(this.outputTopic, producedPressure);

        Properties properties = new Properties();
        this.testDriver = new TopologyTestDriver(builder.build(), properties);
        this.testDriverOutputTopic =
            testDriver
                .createOutputTopic(this.outputTopic, Serdes.String().deserializer(), this.pressureSerde.deserializer());
    }

    @AfterEach
    public void tearDown() {
        testDriver.close();
    }

    @Test
    public void extract() {

        final TestInputTopic<Bytes, PressureAlert>
            testDriverInputTopic =
            testDriver.createInputTopic(this.inputTopic, Serdes.Bytes().serializer(), this.pressureSerde.serializer());
        List<PressureAlert> inputs = Arrays.asList(
            new PressureAlert("101", "2024-09-21T05:25:01.+0200", Integer.MAX_VALUE),
            new PressureAlert("102", "2024-09-21T05:30:02.+0200", Integer.MAX_VALUE),
            new PressureAlert("103", "2024-09-21T05:45:03.+0200", Integer.MAX_VALUE),
            new PressureAlert("104", "DEFINITELY-NOT-PARSABLE!!", Integer.MAX_VALUE),
            new PressureAlert("105", "1500-06-24T09:11:03.+0200", Integer.MAX_VALUE)
        );

        inputs.forEach(pressureAlert ->
            testDriverInputTopic.pipeInput(null, pressureAlert)
        );

        List<TestRecord<String, PressureAlert>> result = readNOutputs(5);

        Optional<TestRecord<String, PressureAlert>> resultOne =
            result.stream().filter(Objects::nonNull).filter(r -> r.value().id().equals("101")).findFirst();
        Optional<TestRecord<String, PressureAlert>> resultTwo =
            result.stream().filter(Objects::nonNull).filter(r -> r.value().id().equals("102")).findFirst();
        Optional<TestRecord<String, PressureAlert>> resultThree =
            result.stream().filter(Objects::nonNull).filter(r -> r.value().id().equals("103")).findFirst();
        Optional<TestRecord<String, PressureAlert>> resultFour =
            result.stream().filter(Objects::nonNull).filter(r -> r.value().id().equals("104")).findFirst();
        Optional<TestRecord<String, PressureAlert>> resultFive =
            result.stream().filter(Objects::nonNull).filter(r -> r.value().id().equals("105")).findFirst();

        assertTrue(resultOne.isPresent());
        assertTrue(resultTwo.isPresent());
        assertTrue(resultThree.isPresent());
        assertFalse(resultFour.isPresent());
        assertFalse(resultFive.isPresent());

        assertEquals(
            formatter.parse("2024-09-21T05:25:01.+0200", Instant::from).toEpochMilli(),
            resultOne.get().timestamp().longValue()
        );

        assertEquals(
            formatter.parse("2024-09-21T05:30:02.+0200", Instant::from).toEpochMilli(),
            resultTwo.get().timestamp().longValue()
        );

        assertEquals(
            formatter.parse("2024-09-21T05:45:03.+0200", Instant::from).toEpochMilli(),
            resultThree.get().timestamp().longValue()
        );
    }
}
