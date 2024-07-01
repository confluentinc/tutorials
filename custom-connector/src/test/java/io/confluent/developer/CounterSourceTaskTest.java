package io.confluent.developer;

import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTaskContext;
import org.apache.kafka.connect.storage.OffsetStorageReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.confluent.developer.CounterSourceTask.CURRENT_ITERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CounterSourceTaskTest {

  @ParameterizedTest(name = "#{index} - Run test with args={0}")
  @NullSource // null starting offset occurs on first run of connector
  @ValueSource(longs = {1, 100})
  public void testTask(Long startingOffset) throws InterruptedException {
    SourceTaskContext mockContext = mock(SourceTaskContext.class);
    OffsetStorageReader mockReader = mock(OffsetStorageReader.class);
    if (startingOffset == null) {
      when(mockReader.offset(anyMap())).thenReturn(null);
    } else {
      when(mockReader.offset(anyMap())).thenReturn(new HashMap() {{
        put(CURRENT_ITERATION, startingOffset.longValue());
      }});
    }
    when(mockContext.offsetStorageReader()).thenReturn(mockReader);

    Map properties = new HashMap() {{
      put("kafka.topic", "my-topic");
      put("interval.ms", "1000");
      put("task.id", "1");
    }};

    CounterSourceTask task = new CounterSourceTask();
    task.initialize(mockContext);
    task.start(properties);

    List<SourceRecord> firstRecords = task.poll();
    List<SourceRecord> secondRecords = task.poll();

    assertEquals(firstRecords.size(), 1);
    assertEquals(secondRecords.size(), 1);

    SourceRecord firstRecord = firstRecords.get(0);
    assertEquals("my-topic", firstRecord.topic());

    long expectedValue = startingOffset == null ? 0 : startingOffset.longValue();

    // offset should be where to pick up, i.e., one more than the value
    assertEquals(expectedValue + 1, firstRecord.sourceOffset().values().iterator().next());
    assertEquals(expectedValue, firstRecord.value());

    SourceRecord secondRecord = secondRecords.get(0);
    assertEquals(expectedValue + 2, secondRecord.sourceOffset().values().iterator().next());
    assertEquals(expectedValue + 1, secondRecord.value());
  }

}
