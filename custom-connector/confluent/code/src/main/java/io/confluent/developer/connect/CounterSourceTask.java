package io.confluent.developer.connect;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CounterSourceTask extends SourceTask {

  public static final String TASK_ID = "task.id";
  public static final String CURRENT_ITERATION = "current.iteration";

  private CounterConnectorConfig config;
  private String topic;
  private long interval;
  private long count = 0L;
  private int taskId;
  private Map sourcePartition;

  @Override
  public String version() {
    return CounterSourceTask.class.getPackage().getImplementationVersion();
  }

  @Override
  public void start(Map<String, String> props) {
    config = new CounterConnectorConfig(props);
    topic = config.getKafkaTopic();
    interval = config.getInterval();
    taskId = Integer.parseInt(props.get(TASK_ID));
    sourcePartition = Collections.singletonMap(TASK_ID, taskId);

    Map<String, Object> offset = context.offsetStorageReader().offset(sourcePartition);
    if (offset != null) {
      // the offset contains our next state, so restore it as is
      count = ((Long) offset.get(CURRENT_ITERATION));
    }
  }

  @Override
  public List<SourceRecord> poll() throws InterruptedException {

    if (interval > 0) {
      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        Thread.interrupted();
        return null;
      }
    }

    Map sourceOffset = Collections.singletonMap(CURRENT_ITERATION, count + 1);

    final List<SourceRecord> records = Collections.singletonList(new SourceRecord(
        sourcePartition,
        sourceOffset,
        topic,
        null,
        Schema.INT64_SCHEMA,
        count
    ));
    count++;
    return records;
  }

  @Override
  public void stop() {
  }
}
