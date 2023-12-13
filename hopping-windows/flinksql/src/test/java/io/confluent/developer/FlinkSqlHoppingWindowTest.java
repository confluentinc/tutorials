package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FlinkSqlHoppingWindowTest extends AbstractFlinkKafkaTest {

  @Test
  public void testHoppingWindows() throws Exception {
    // create base temperature table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-temperature-readings.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-temperature-readings.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-temperature-by-10min-window.sql"));

    // Compare actual and expected results. Convert result output to line sets to compare so that order
    // doesn't matter.
    String actualTableauResults = tableauResults(tableResult);
    String expectedTableauResults = getResourceFileContents("expected-temperature-by-10min-window.txt");
    assertEquals(stringToLineSet(expectedTableauResults), stringToLineSet(actualTableauResults));
  }

}
