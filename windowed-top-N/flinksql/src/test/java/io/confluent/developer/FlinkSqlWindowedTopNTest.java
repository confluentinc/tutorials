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

public class FlinkSqlWindowedTopNTest extends AbstractFlinkKafkaTest {

  @Test
  public void testWindowedTopN() throws Exception {
    
    streamTableEnv.getConfig().set("table.exec.source.idle-timeout", "5 ms");
    streamTableEnv.executeSql(getResourceFileContents("create-movie-views.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();

    streamTableEnv.executeSql(getResourceFileContents("populate-movie-starts.sql")).await();

    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-movie-starts-for-top-categories-per-hour-dynamic.sql"));

    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedResults = getExpectedFinalUpdateRowObjects();
    assertEquals(expectedResults, actualResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2024-04-23 19:00:00"), yyyy_MM_dd("2024-04-23 20:00:00"), "Crime", 2L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2024-04-23 20:00:00"), yyyy_MM_dd("2024-04-23 21:00:00"), "Sci-Fi", 3L,  1L));
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2024-04-23 21:00:00"), yyyy_MM_dd("2024-04-23 22:00:00"), "Drama", 1L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2024-04-23 22:00:00"), yyyy_MM_dd("2024-04-23 23:00:00"), "Animation", 2L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, yyyy_MM_dd("2024-04-23 23:00:00"), yyyy_MM_dd("2024-04-24 00:00:00"), "Animation", 1L, 1L));
    return rowList;
  }

}
