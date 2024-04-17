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

public class FlinkSqlTopNTest extends AbstractFlinkKafkaTest {

  @Test
  public void testTopN() throws Exception {
    // create base movie sales table and aggregation table, and populate with test data
    streamTableEnv.getConfig().set("table.exec.source.idle-timeout", "5 ms");
    streamTableEnv.executeSql(getResourceFileContents("create-movie-views.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-movie-views.sql")).await();

    // execute query on result table that should have movie sales aggregated by release year
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-movie-views-for-top-5-dynamic.sql"));

    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedResults = getExpectedFinalUpdateRowObjects();
    assertEquals(expectedResults, actualResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, "The Dark Knight", "Action", 100240L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "The Dark Knight", "Action", 100240L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Avengers: Endgame", "Action", 200010L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Dark Knight", "Action", 100240L, 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Inception", "Sci-Fi", 150000L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Joker", "Drama", 120304L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "The Godfather", "Crime", 300202L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Casablanca", "Romance", 400400L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Joker", "Drama", 120304L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Shawshank Redemption", "Drama", 500056L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Joker", "Drama", 120304L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Joker", "Drama", 120304L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Forrest Gump", "Drama", 350345L, 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Joker", "Drama", 120304L, 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Joker", "Drama", 120304L, 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Fight Club", "Drama", 250250L, 3L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Pulp Fiction", "Crime", 160160L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Pulp Fiction", "Crime", 160160L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Godfather: Part II", "Crime", 170170L, 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Pulp Fiction", "Crime", 160160L, 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "The Godfather: Part II", "Crime", 170170L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Departed", "Crime", 180180L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Pulp Fiction", "Crime", 160160L, 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Godfather: Part II", "Crime", 170170L, 3L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Toy Story 3", "Animation", 190190L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Toy Story 3", "Animation", 190190L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Up", "Animation", 200200L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Toy Story 3", "Animation", 190190L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Up", "Animation", 200200L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Lion King", "Animation", 210210L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Toy Story 3", "Animation", 190190L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Up", "Animation", 200200L, 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Toy Story 3", "Animation", 190190L, 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Inception", "Sci-Fi", 150000L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Star Wars: The Force Awakens", "Sci-Fi", 220220L, 1L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Inception", "Sci-Fi", 150000L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Star Wars: The Force Awakens", "Sci-Fi", 220220L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Matrix", "Sci-Fi", 230230L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Inception", "Sci-Fi", 150000L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Star Wars: The Force Awakens", "Sci-Fi", 220220L, 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Inception", "Sci-Fi", 150000L, 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "The Matrix", "Sci-Fi", 230230L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Interstellar", "Sci-Fi", 240240L, 1L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Star Wars: The Force Awakens", "Sci-Fi", 220220L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "The Matrix", "Sci-Fi", 230230L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Inception", "Sci-Fi", 150000L, 3L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Star Wars: The Force Awakens", "Sci-Fi", 220220L, 3L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Titanic", "Romance", 250250L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, "Titanic", "Romance", 250250L, 2L));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, "Pride and Prejudice", "Romance", 260260L, 2L));
    rowList.add(Row.ofKind(RowKind.INSERT, "Titanic", "Romance", 250250L, 3L));
    return rowList;
  }

}
