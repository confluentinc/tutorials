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

public class FlinkSqlOverAggregationTest extends AbstractFlinkKafkaTest {

  @Test
  public void testOverAggregation() throws Exception {
    // create base movie sales table and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-movie-views.sql.template",
       Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-movie-starts.sql")).await();

    // execute query on result table that should have movie sales aggregated by release year
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("over-aggregation-query.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, "The Dark Knight", "Action", yyyy_MM_dd("2024-04-23 19:04:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Godfather", "Crime", yyyy_MM_dd("2024-04-23 19:13:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Matrix", "Sci-Fi", yyyy_MM_dd("2024-04-23 19:25:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Godfather: Part II", "Crime", yyyy_MM_dd("2024-04-23 19:28:00"), 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Interstellar", "Sci-Fi", yyyy_MM_dd("2024-04-23 20:14:00"), 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Shawshank Redemption", "Drama", yyyy_MM_dd("2024-04-23 20:20:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Inception", "Sci-Fi", yyyy_MM_dd("2024-04-23 20:24:00"), 3L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Titanic", "Romance", yyyy_MM_dd("2024-04-23 20:25:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Casablanca", "Romance", yyyy_MM_dd("2024-04-23 20:26:00"), 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Star Wars: The Force Awakens", "Sci-Fi", yyyy_MM_dd("2024-04-23 20:42:00"), 3L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Forrest Gump", "Drama", yyyy_MM_dd("2024-04-23 21:54:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Avengers: Endgame", "Action", yyyy_MM_dd("2024-04-23 22:01:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Pulp Fiction", "Crime", yyyy_MM_dd("2024-04-23 22:09:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Up", "Animation", yyyy_MM_dd("2024-04-23 22:17:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Lion King", "Animation", yyyy_MM_dd("2024-04-23 22:28:00"), 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Joker", "Drama", yyyy_MM_dd("2024-04-23 22:56:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Departed", "Crime", yyyy_MM_dd("2024-04-23 23:11:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Toy Story 3", "Animation", yyyy_MM_dd("2024-04-23 23:12:00"), 3L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Fight Club", "Drama", yyyy_MM_dd("2024-04-23 23:24:00"), 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Pride and Prejudice", "Romance", yyyy_MM_dd("2024-04-23 23:37:00"), 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Pride of Archbishop Carroll", "History", yyyy_MM_dd("2024-04-24 03:37:00"), 1L));
    return rowList;
  }

}
