package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FlinkSqlAggregatingCountTest extends AbstractFlinkKafkaTest {

  @Test
  public void testCountAggregation() throws Exception {
    // create base movie sales table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-movie-sales.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-movie-sales.sql")).await();

    // execute query that counts ticket sales per title
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-movie-sales-by-title.sql"));

    // Compare actual and expected results. Convert result output to line sets to compare so that order
    // doesn't matter, because the grouped result order doesn't matter -- 2017's could come before or after 2019's.
    String actualTableauResults = tableauResults(tableResult);
    String expectedTableauResults = getResourceFileContents("expected-movie-sales-by-title.txt");
    assertEquals(stringToLineSet(actualTableauResults), stringToLineSet(expectedTableauResults));
  }

}
