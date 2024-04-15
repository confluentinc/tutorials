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
import static org.junit.Assert.*;

public class FlinkSqlFindingDistinctWindowedTest extends AbstractFlinkKafkaTest {

  @Test
  public void testFindDistinct() throws Exception {
    // create base movie sales table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-clicks-table.sql.template",
        Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-clicks.sql")).await();

    // execute query to get deduplicated clicks
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-deduplicated-clicks.sql"));
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedResults = getExpectedFinalUpdateRowObjects();
    assertEquals(expectedResults, actualResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2023-07-09 01:00:00"), yyyy_MM_dd("2023-07-09 02:00:00"), "10.0.0.1", "https://acme.com/index.html"));
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2023-07-09 01:00:00"), yyyy_MM_dd("2023-07-09 02:00:00"), "10.0.0.12", "https://amazon.com/index.html"));
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2023-07-09 01:00:00"), yyyy_MM_dd("2023-07-09 02:00:00"), "10.0.0.13", "https://confluent/index.html"));
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2023-07-09 02:00:00"), yyyy_MM_dd("2023-07-09 03:00:00"), "10.0.0.1", "https://acme.com/index.html"));
    return rowList;
  }

}
