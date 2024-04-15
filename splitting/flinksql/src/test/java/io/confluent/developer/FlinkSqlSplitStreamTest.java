package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.confluent.developer.TestUtils.rowObjectsFromTableResult;
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

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, "Matt Damon", "The Martian"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Meryl Streep", "The Iron Lady"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Russell Crowe", "Gladiator"));
    return rowList;
  }
}
