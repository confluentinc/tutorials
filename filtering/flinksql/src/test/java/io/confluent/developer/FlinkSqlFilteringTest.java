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

public class FlinkSqlFilteringTest extends AbstractFlinkKafkaTest {

  @Test
  public void testFilter() throws Exception {
    // create base publications table and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-all-publications.sql.template",
            Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-publication-events.sql")).await();

    // execute query on result table that should have publications for 1 author
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-publications-by-author.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 1, "A Song of Ice and Fire"));
    rowList.add(Row.ofKind(RowKind.INSERT, 3, "Fire & Blood"));
    rowList.add(Row.ofKind(RowKind.INSERT, 6, "A Dream of Spring"));
    rowList.add(Row.ofKind(RowKind.INSERT, 8, "The Ice Dragon"));
    return rowList;
  }
}
