package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.confluent.developer.TestUtils.rowObjectsFromTableResult;
import static io.confluent.developer.TestUtils.yyyy_MM_dd;
import static org.junit.Assert.assertEquals;

public class FlinkSqlHoppingWindowTest extends AbstractFlinkKafkaTest {

  @Test
  public void testHoppingWindows() throws Exception {
    // create base temperature table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-temperature-readings.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-temperature-readings.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-temperature-by-10min-window.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 55.0, yyyy_MM_dd("2023-01-15 02:10:00"), yyyy_MM_dd("2023-01-15 02:20:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 52.5, yyyy_MM_dd("2023-01-15 02:15:00"), yyyy_MM_dd("2023-01-15 02:25:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 47.5, yyyy_MM_dd("2023-01-15 02:20:00"), yyyy_MM_dd("2023-01-15 02:30:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 42.5, yyyy_MM_dd("2023-01-15 02:25:00"), yyyy_MM_dd("2023-01-15 02:35:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 42.5, yyyy_MM_dd("2023-01-15 02:30:00"), yyyy_MM_dd("2023-01-15 02:40:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 47.5, yyyy_MM_dd("2023-01-15 02:35:00"), yyyy_MM_dd("2023-01-15 02:45:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 52.5, yyyy_MM_dd("2023-01-15 02:40:00"), yyyy_MM_dd("2023-01-15 02:50:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 57.5, yyyy_MM_dd("2023-01-15 02:45:00"), yyyy_MM_dd("2023-01-15 02:55:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 60.0, yyyy_MM_dd("2023-01-15 02:50:00"), yyyy_MM_dd("2023-01-15 03:00:00")));
    return rowList;
  }

}
