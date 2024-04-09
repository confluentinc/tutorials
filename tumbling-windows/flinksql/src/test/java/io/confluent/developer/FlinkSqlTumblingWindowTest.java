package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.confluent.developer.TestUtils.*;
import static org.junit.Assert.assertEquals;

public class FlinkSqlTumblingWindowTest extends AbstractFlinkKafkaTest {

  @Test
  public void testTumblingWindows() throws Exception {
    // create base ratings table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-ratings.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-ratings.sql")).await();
   
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-ratings-by-6hr-window.sql"));

    List<Row> actualResults = rowObjectsFromTableResult(tableResult);

    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    Row actualFirstRow = actualResults.get(0);
    Row expectedFirstRow = expectedRowResults.get(0);
    assertEquals(actualFirstRow,expectedFirstRow);

  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, "Die Hard", 2L, 6.35D, yyyy_MM_dd("2023-07-09 00:00:00"), yyyy_MM_dd("2023-07-09 06:00:00")));
    return rowList;
  }

}
