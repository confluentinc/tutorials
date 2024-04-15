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

public class FlinkSqlAggregatingCountTest extends AbstractFlinkKafkaTest {

  @Test
  public void testCountAggregation() throws Exception {
    // create base movie sales table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-movie-sales.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-movie-sales.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-movie-sales-by-title.sql"));

    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedResults = getExpectedFinalUpdateRowObjects();
    assertEquals(expectedResults, actualResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, "Aliens", 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Die Hard", 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Die Hard", 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Die Hard", 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Godfather", 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Die Hard", 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Die Hard", 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "The Godfather", 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Godfather", 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Big Lebowski", 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "The Big Lebowski", 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Big Lebowski", 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "The Godfather", 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Godfather", 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "The Godfather", 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Godfather", 4L));
    return rowList;
  }

}
