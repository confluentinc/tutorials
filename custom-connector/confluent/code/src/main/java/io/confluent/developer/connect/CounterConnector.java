package io.confluent.developer.connect;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CounterConnector extends SourceConnector {

  private Map<String, String> props;

  @Override
  public String version() {
    return CounterConnector.class.getPackage().getImplementationVersion();
  }

  @Override
  public void start(Map<String, String> props) {
    this.props = props;
  }

  @Override
  public void stop() {
  }

  @Override
  public Class<? extends Task> taskClass() {
    return CounterSourceTask.class;
  }

  @Override
  public List<Map<String, String>> taskConfigs(int maxTasks) {
    List<Map<String, String>> taskConfigs = new ArrayList<>();
    for (int i = 0; i < maxTasks; i++) {
      Map<String, String> taskConfig = new HashMap<>(this.props);
      taskConfig.put(CounterSourceTask.TASK_ID, Integer.toString(i));
      taskConfigs.add(taskConfig);
    }
    return taskConfigs;
  }

  @Override
  public ConfigDef config() {
    return CounterConnectorConfig.conf();
  }
}
