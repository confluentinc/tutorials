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

public class FlinkSqlMergeTablesTest extends AbstractFlinkKafkaTest {

  @Test
  public void testMerge() throws Exception {
    // create and populate source rock / classical song tables
    streamTableEnv.executeSql(getResourceFileContents("create-rock-songs.sql.template",
            Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-rock-songs.sql")).await();
    streamTableEnv.executeSql(getResourceFileContents("create-classical-songs.sql.template",
            Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-classical-songs.sql")).await();

    // create and populate merged table
    streamTableEnv.executeSql(getResourceFileContents("create-all-songs.sql.template",
            Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-all-songs-from-rock.sql"));
    streamTableEnv.executeSql(getResourceFileContents("populate-all-songs-from-classical.sql"));

    // query merged table, and compare actual and expected results
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-all-songs.sql"));
    List<Row> actualResults = rowObjectsFromTableResult(tableResult, 9);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, "Metallica", "Fade to Black", "rock"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Smashing Pumpkins", "Today", "rock"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Pink Floyd", "Another Brick in the Wall", "rock"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Van Halen", "Jump", "rock"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Led Zeppelin", "Kashmir", "rock"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Wolfgang Amadeus Mozart", "The Magic Flute", "classical"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Johann Pachelbel", "Canon", "classical"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Ludwig van Beethoven", "Symphony No. 5", "classical"));
    rowList.add(Row.ofKind(RowKind.INSERT, "Edward Elgar", "Pomp and Circumstance", "classical"));
    return rowList;
  }
}
