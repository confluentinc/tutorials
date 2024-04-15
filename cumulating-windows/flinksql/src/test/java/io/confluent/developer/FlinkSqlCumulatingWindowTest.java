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

public class FlinkSqlCumulatingWindowTest extends AbstractFlinkKafkaTest {

  @Test
  public void testCumulatingWindows() throws Exception {
    // create base orders table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-orders.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-orders.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-revenue-per-hour-cumulating.sql"));
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedResults = getExpectedFinalUpdateRowObjects();
    assertEquals(expectedResults, actualResults);

  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 13.96, yyyy_MM_dd("2023-01-15 02:00:00"), yyyy_MM_dd("2023-01-15 02:05:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 33.94, yyyy_MM_dd("2023-01-15 02:00:00"), yyyy_MM_dd("2023-01-15 02:10:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 9.99, yyyy_MM_dd("2023-01-15 02:10:00"), yyyy_MM_dd("2023-01-15 02:20:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 26.67, yyyy_MM_dd("2023-01-15 02:20:00"), yyyy_MM_dd("2023-01-15 02:25:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 61.57, yyyy_MM_dd("2023-01-15 02:20:00"), yyyy_MM_dd("2023-01-15 02:30:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 32.0, yyyy_MM_dd("2023-01-15 02:30:00"), yyyy_MM_dd("2023-01-15 02:35:00")));
    rowList.add(Row.ofKind(RowKind.INSERT, 32.0, yyyy_MM_dd("2023-01-15 02:30:00"), yyyy_MM_dd("2023-01-15 02:40:00")));
    return rowList;
  }

}
