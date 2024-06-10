/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end integration test that demonstrates how to reorder the stream of incoming messages
 * by the timestamp embedded in the message payload.
 * <p>
 * Makes sense only on per-partition basis.
 * <p>
 * Reordering occurs within time windows defined by the
 *
 * Note: This example uses lambda expressions and thus works with Java 8+ only.
 */
class ReorderProcessorTest {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static long parseTimeStringToLong(final String timeString) throws ParseException {
        return dateFormat.parse(timeString).getTime();
    }

    @Test
    void shouldReorderTheInput() throws ParseException {

        String inputTopic = ReorderStreams.INPUT;
        String outputTopic = ReorderStreams.OUTPUT;
        ReorderStreams reorderStreams = new ReorderStreams();
        Serde<String> stringSerde = Serdes.String();
        Serde<Event> eventSerde = StreamsSerde.serdeFor(Event.class);
        Properties props = new Properties();
        try (TopologyTestDriver driver = new TopologyTestDriver(reorderStreams.buildTopology(props))) {

             TestInputTopic<String,Event> testInputTopic  = driver.createInputTopic(inputTopic,stringSerde.serializer(), eventSerde.serializer());
             TestOutputTopic<String, Event> testOutputTopic  = driver.createOutputTopic(outputTopic, stringSerde.deserializer(), eventSerde.deserializer());
             
            //  Input not ordered by time
            final List<KeyValue<String,Event>> inputValues = Arrays.asList(
                    KeyValue.pair("A", new Event("A", parseTimeStringToLong("2021-11-03 23:00:00Z"))),    // stream time calibration
                    KeyValue.pair("B", new Event("B", parseTimeStringToLong("2021-11-04 01:05:00Z"))),    // 10-hours interval border is at "2021-11-04 01:00:00Z"
                    KeyValue.pair("C", new Event("C", parseTimeStringToLong("2021-11-04 01:10:00Z"))),
                    KeyValue.pair("D", new Event("D", parseTimeStringToLong("2021-11-04 01:40:00Z"))),
                    KeyValue.pair("E", new Event("E", parseTimeStringToLong("2021-11-04 02:25:00Z"))),
                    KeyValue.pair("F", new Event("F", parseTimeStringToLong("2021-11-04 01:20:00Z"))),
                    KeyValue.pair("G", new Event("G", parseTimeStringToLong("2021-11-04 02:45:00Z"))),
                    KeyValue.pair("H", new Event("H", parseTimeStringToLong("2021-11-04 02:00:00Z"))),
                    KeyValue.pair("I", new Event("I", parseTimeStringToLong("2021-11-04 03:00:00Z"))),
                    KeyValue.pair("J", new Event("J", parseTimeStringToLong("2021-11-04 02:40:00Z"))),
                    KeyValue.pair("K", new Event("K", parseTimeStringToLong("2021-11-04 02:20:00Z"))),    // 10-hours interval border is at "2021-11-04 11:00:00Z"
                    KeyValue.pair("L", new Event("L", parseTimeStringToLong("2021-11-05 00:00:00Z")))     // stream time calibration
            );

            //  Expected ordered by time
            final List<KeyValue<String,Event>> expectedValues = Arrays.asList(
                    KeyValue.pair("A", new Event("A", parseTimeStringToLong("2021-11-03 23:00:00Z"))),    // stream time calibration
                    KeyValue.pair("B", new Event("B", parseTimeStringToLong("2021-11-04 01:05:00Z"))),
                    KeyValue.pair("C", new Event("C", parseTimeStringToLong("2021-11-04 01:10:00Z"))),
                    KeyValue.pair("F", new Event("F", parseTimeStringToLong("2021-11-04 01:20:00Z"))),
                    KeyValue.pair("D", new Event("D", parseTimeStringToLong("2021-11-04 01:40:00Z"))),
                    KeyValue.pair("H", new Event("H", parseTimeStringToLong("2021-11-04 02:00:00Z"))),
                    KeyValue.pair("K", new Event("K", parseTimeStringToLong("2021-11-04 02:20:00Z"))),
                    KeyValue.pair("E", new Event("E", parseTimeStringToLong("2021-11-04 02:25:00Z"))),
                    KeyValue.pair("J", new Event("J", parseTimeStringToLong("2021-11-04 02:40:00Z"))),
                    KeyValue.pair("G", new Event("G", parseTimeStringToLong("2021-11-04 02:45:00Z"))),
                    KeyValue.pair("I", new Event("I", parseTimeStringToLong("2021-11-04 03:00:00Z"))),
                    KeyValue.pair("L", new Event("L", parseTimeStringToLong("2021-11-05 00:00:00Z")))
            );
            Instant now = Instant.now();
            var recordCount = 1;
            var secondsToAdvance = 60L;
            
              for (KeyValue<String,Event> input : inputValues) {
                  testInputTopic.pipeInput(input.key, input.value, now.plusSeconds(recordCount++ * secondsToAdvance));
                  if (recordCount == 11) {
                      // Need to advance stream-time beyond 10 hours to trigger punctuation
                      secondsToAdvance = 60 * 60 * 10;
                  }
              }
              List<KeyValue<String, Event>> actualValues = testOutputTopic.readKeyValuesToList();
              assertEquals(expectedValues.size(), actualValues.size());
              assertEquals(expectedValues, actualValues);
        }

    }
}