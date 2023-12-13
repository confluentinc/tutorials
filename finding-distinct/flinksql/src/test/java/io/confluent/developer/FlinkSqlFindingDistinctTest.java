package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class FlinkSqlFindingDistinctTest extends AbstractFlinkKafkaTest {

  @Test
  public void testFindDistinct() throws Exception {
    // create base movie sales table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-clicks-table.sql.template",
        Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-clicks.sql")).await();

    // execute query to get deduplicated clicks
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-deduplicated-clicks.sql"));

    // Compare actual and expected results. Convert result output to line sets to compare so that order
    // doesn't matter
    String actualTableauResults = tableauResults(tableResult);
    String expectedTableauResults = getResourceFileContents("expected-deduplicated-clicks.txt");
    assertEquals(stringToLineSet(actualTableauResults), stringToLineSet(expectedTableauResults));
  }

}
