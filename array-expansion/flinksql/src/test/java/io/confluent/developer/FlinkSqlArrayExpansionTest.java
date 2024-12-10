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

public class FlinkSqlArrayExpansionTest extends AbstractFlinkKafkaTest {

  @Test
  public void testArrayExpansion() throws Exception {
    // create base traveler locations table and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-traveler-locations.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-traveler-locations.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-cities-array-expansion.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, "Jane Smith", "Rome"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Jane Smith", "Paris"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Xander Jackson", "Berlin"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Xander Jackson", "Paris"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Sally Stewart", "Lisbon"));
    return rowList;
  }

}
