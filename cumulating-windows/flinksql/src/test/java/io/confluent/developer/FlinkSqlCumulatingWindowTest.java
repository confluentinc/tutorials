package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FlinkSqlCumulatingWindowTest extends AbstractFlinkKafkaTest {

  @Test
  public void testCumulatingWindows() throws Exception {
    // create base orders table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-orders.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-orders.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-revenue-per-hour-cumulating.sql"));

    // Compare actual and expected results. Convert result output to line sets to compare so that order
    // doesn't matter.
    String actualTableauResults = tableauResults(tableResult);
    String expectedTableauResults = getResourceFileContents("expected-revenue-per-hour-cumulating.txt");
    assertEquals(stringToLineSet(expectedTableauResults), stringToLineSet(actualTableauResults));
  }

}
