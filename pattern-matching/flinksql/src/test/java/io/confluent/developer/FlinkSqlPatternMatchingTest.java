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

public class FlinkSqlPatternMatchingTest extends AbstractFlinkKafkaTest {

  @Test
  public void testPatternMatching() throws Exception {
    // create base movie sales table and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-temperature-readings.sql.template",
       Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-temperature-readings.sql")).await();

    // execute query on result table that should have movie sales aggregated by release year
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-temperature-momentum-by-sensor.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult, 2);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults.get(0), expectedRowResults.get(0));
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 1,  yyyy_MM_dd("2023-04-03 02:00:01"), 40.0, yyyy_MM_dd("2023-04-03 02:00:13"), 47.0));
    rowList.add(Row.ofKind(RowKind.INSERT, 2,  yyyy_MM_dd("2023-04-03 02:00:02"), 59.0, yyyy_MM_dd("2023-04-03 02:00:14"), 53.0));
    return rowList;
  }

}
