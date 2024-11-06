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

public class FlinkSqlOrderByTest extends AbstractFlinkKafkaTest {

  @Test
  public void testOrderBy() throws Exception {
    // create base temperature table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-temperature-readings.sql.template",
            Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-temperature-readings.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-temperature-order-by-ts.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 55.0, yyyy_MM_dd("2024-11-01 02:10:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 55.0, yyyy_MM_dd("2024-11-01 02:15:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 50.0, yyyy_MM_dd("2024-11-01 02:20:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 45.0, yyyy_MM_dd("2024-11-01 02:25:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 40.0, yyyy_MM_dd("2024-11-01 02:30:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 45.0, yyyy_MM_dd("2024-11-01 02:35:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 50.0, yyyy_MM_dd("2024-11-01 02:40:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 55.0, yyyy_MM_dd("2024-11-01 02:45:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 60.0, yyyy_MM_dd("2024-11-01 02:50:30")));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, 60.0, yyyy_MM_dd("2024-11-01 02:53:30")));
    return rowList;
  }

}
