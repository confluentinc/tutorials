package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FlinkSqlSplitStreamTest extends AbstractFlinkKafkaTest {

  @Test
  public void testSplit() throws Exception {
    // create base acting events table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-acting-events.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-acting-events.sql")).await();
    streamTableEnv.executeSql(getResourceFileContents("create-acting-events-drama.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();

    // execute query on result table that should have drama acting events
    streamTableEnv.executeSql(getResourceFileContents("populate-acting-events-drama.sql")).await();
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-acting-events-drama.sql"));

    // Compare actual and expected results. Convert result output to line sets to compare so that order
    // doesn't matter.
    String actualTableauResults = tableauResults(tableResult);
    String expectedTableauResults = getResourceFileContents("expected-acting-events-drama.txt");
    assertEquals(stringToLineSet(actualTableauResults), stringToLineSet(expectedTableauResults));
  }

}
