package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FlinkSqlFilteringTest extends AbstractFlinkKafkaTest {

  @Test
  public void testFilter() throws Exception {
    // create base movie sales table and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-all-publications.sql.template",
       Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-publication-events.sql")).await();

    // execute query on result table that should have movie sales aggregated by release year
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-publications-by-author.sql"));

    // Compare actual and expected results
    String actualTableauResults = tableauResults(tableResult);
    String expectedTableauResults = getResourceFileContents("expected-publications-by-author.txt");
    assertEquals(stringToLineSet(actualTableauResults), stringToLineSet(expectedTableauResults));
  }

}
