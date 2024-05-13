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

public class FlinkSqlSessionWindowsTest extends AbstractFlinkKafkaTest {

  @Test
  public void testSessionWindows() throws Exception {
    // create and populate clicks
    streamTableEnv.executeSql(getResourceFileContents("create-clicks-table.sql.template",
            Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-clicks.sql")).await();


    // query merged table, and compare actual and expected results
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-click-sessions.sql"));
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    //TODO create expected results for session window
    return rowList;
  }
}
