package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import static io.confluent.developer.WindowFinalResult.INPUT_TOPIC;
import static io.confluent.developer.WindowFinalResult.OUTPUT_TOPIC;
import static org.apache.kafka.streams.kstream.WindowedSerdes.timeWindowedSerdeFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WindowFinalResultTest {

  private TopologyTestDriver testDriver;
  private WindowFinalResult windowFinalResult = new WindowFinalResult();
  private TestOutputTopic<Windowed<String>, Integer> testOutputTopic;
  private Serde<PressureAlert> pressureSerde;

  private final String inputTopic = INPUT_TOPIC;
  private final String outputTopic = OUTPUT_TOPIC;

  private final Duration testWindowSize = Duration.ofSeconds(10);
  private final Duration testGracePeriodSize = Duration.ofSeconds(20);
  private final Serde<Windowed<String>> keyResultSerde = timeWindowedSerdeFrom(String.class, testWindowSize.toMillis());

  private TimeWindows makeFixedTimeWindow() {
    return TimeWindows.ofSizeAndGrace(testWindowSize,testGracePeriodSize).advanceBy(testWindowSize);
  }

  private List<TestRecord<Windowed<String>, Integer>> readAtLeastNOutputs(int size) {
    final List<TestRecord<Windowed<String>, Integer>> testRecords = testOutputTopic.readRecordsToList();
    assertThat(testRecords.size(), equalTo(size));

    return testRecords;
  }

  @BeforeEach
  public void setUp() {
    this.pressureSerde = StreamsSerde.serdeFor(PressureAlert.class);
    Topology topology = windowFinalResult.buildTopology(new Properties());
    this.testDriver = new TopologyTestDriver(topology, new Properties());
    this.testOutputTopic =
        testDriver.createOutputTopic(outputTopic, this.keyResultSerde.deserializer(), Serdes.Integer().deserializer());
  }

  @AfterEach
  public void tearDown() {
    testDriver.close();
  }

  @Test
  public void topologyShouldGroupOverDatetimeWindows() {
    final TestInputTopic<Bytes, PressureAlert>
            testDriverInputTopic =
            testDriver.createInputTopic(this.inputTopic, Serdes.Bytes().serializer(), this.pressureSerde.serializer());

    List<PressureAlert> inputs = Arrays.asList(
            new PressureAlert("101", "2025-09-15T05:30:01.+0200", Integer.MAX_VALUE),
            new PressureAlert("101", "2025-09-15T05:30:02.+0200", Integer.MAX_VALUE),
            new PressureAlert("101", "2025-09-15T05:30:03.+0200", Integer.MAX_VALUE),
            new PressureAlert("101", "2025-09-15T05:45:01.+0200", Integer.MAX_VALUE),
            new PressureAlert("101", "2025-09-15T05:45:03.+0200", Integer.MAX_VALUE),
            new PressureAlert("101", "2025-09-15T05:55:10.+0200", Integer.MAX_VALUE),
            // ONE LAST EVENT TO TRIGGER TO MOVE THE STREAMING TIME
            new PressureAlert("XXX", "2025-09-15T05:55:40.+0200", Integer.MAX_VALUE)
    );

    inputs.forEach(pressureAlert ->
            testDriverInputTopic.pipeInput(null, pressureAlert)
    );

    List<TestRecord<Windowed<String>, Integer>> result = readAtLeastNOutputs(3);

    Optional<TestRecord<Windowed<String>, Integer>> resultOne = result
            .stream().filter(Objects::nonNull).filter(r -> r.key().window().start() == 1757907000000L).findAny();
    Optional<TestRecord<Windowed<String>, Integer>> resultTwo = result
            .stream().filter(Objects::nonNull).filter(r -> r.key().window().start() == 1757907900000L).findAny();
    Optional<TestRecord<Windowed<String>, Integer>> resultThree = result
            .stream().filter(Objects::nonNull).filter(r -> r.key().window().start() == 1757908510000L).findAny();

    assertTrue(resultOne.isPresent());
    assertTrue(resultTwo.isPresent());
    assertTrue(resultThree.isPresent());

    assertEquals(3, resultOne.get().value().intValue());
    assertEquals(2, resultTwo.get().value().intValue());
    assertEquals(1, resultThree.get().value().intValue());

    result.forEach((element) ->
            assertEquals(
                    makeFixedTimeWindow().size(),
                    element.key().window().end() - element.key().window().start()
            )
    );
  }

  @Test
  public void topologyShouldGroupById() {

    final TestInputTopic<Bytes, PressureAlert>
            testDriverInputTopic =
            testDriver.createInputTopic(this.inputTopic, Serdes.Bytes().serializer(), this.pressureSerde.serializer());

    List<PressureAlert> inputs = Arrays.asList(
            new PressureAlert("101", "2025-09-15T05:30:01.+0200", Integer.MAX_VALUE),
            new PressureAlert("101", "2025-09-15T05:30:02.+0200", Integer.MAX_VALUE),
            new PressureAlert("101", "2025-09-15T05:30:03.+0200", Integer.MAX_VALUE),
            new PressureAlert("102", "2025-09-15T05:30:01.+0200", Integer.MAX_VALUE),
            new PressureAlert("102", "2025-09-15T05:30:02.+0200", Integer.MAX_VALUE),
            new PressureAlert("102", "2025-09-15T05:30:03.+0200", Integer.MAX_VALUE),
            new PressureAlert("103", "2025-09-15T05:30:01.+0200", Integer.MAX_VALUE),
            new PressureAlert("103", "2025-09-15T05:30:02.+0200", Integer.MAX_VALUE),
            new PressureAlert("103", "2025-09-15T05:30:03.+0200", Integer.MAX_VALUE),
            // One final event to move the streaming time
            new PressureAlert("XXX", "2025-09-15T05:55:41.+0200", Integer.MAX_VALUE)
    );

    inputs.forEach(pressureAlert ->
            testDriverInputTopic.pipeInput(null, pressureAlert)
    );

    List<TestRecord<Windowed<String>, Integer>> result = readAtLeastNOutputs(3);

    Optional<TestRecord<Windowed<String>, Integer>> resultOne =
            result.stream().filter(Objects::nonNull).filter(r -> r.key().key().equals("101")).findAny();
    Optional<TestRecord<Windowed<String>, Integer>> resultTwo =
            result.stream().filter(Objects::nonNull).filter(r -> r.key().key().equals("102")).findAny();
    Optional<TestRecord<Windowed<String>, Integer>> resultThree =
            result.stream().filter(Objects::nonNull).filter(r -> r.key().key().equals("103")).findAny();

    assertTrue(resultOne.isPresent());
    assertTrue(resultTwo.isPresent());
    assertTrue(resultThree.isPresent());

    assertEquals(3, resultOne.get().value().intValue());
    assertEquals(3, resultTwo.get().value().intValue());
    assertEquals(3, resultThree.get().value().intValue());
  }
}
