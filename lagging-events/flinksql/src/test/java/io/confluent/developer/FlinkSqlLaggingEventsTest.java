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

public class FlinkSqlLaggingEventsTest extends AbstractFlinkKafkaTest {

  @Test
  public void testLaggingEvents() throws Exception {
    // create base temperature table and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-temperature-readings.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-temperature-readings.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-temperature-and-previous-temperature.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 0, yyyy_MM_dd("2024-11-15 02:15:30"), 55.0, null));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, yyyy_MM_dd("2024-11-15 02:20:30"), 50.0, 55.0));
    rowList.add(Row.ofKind(RowKind.INSERT, 0, yyyy_MM_dd("2024-11-15 02:25:30"), 45.0, 50.0));
    return rowList;
  }

}
