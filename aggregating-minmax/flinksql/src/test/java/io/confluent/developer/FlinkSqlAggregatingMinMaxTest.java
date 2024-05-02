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

public class FlinkSqlAggregatingMinMaxTest extends AbstractFlinkKafkaTest {

  @Test
  public void testMinMax() throws Exception {
    // create base movie sales table and aggregation table, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-movie-sales.sql.template",
        Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-movie-sales.sql")).await();

    // execute query on result table that should have movie sales aggregated by release year
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-movie-sales-by-year.sql"));

    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedResults = getExpectedFinalUpdateRowObjects();
    assertEquals(expectedResults, actualResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();

    rowList.add(Row.ofKind(RowKind.INSERT, 2019, 856980506, 856980506));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, 2019, 856980506, 856980506));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, 2019, 426829839, 856980506));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, 2019, 426829839, 856980506));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, 2019, 401486230, 856980506));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, 2019, 401486230, 856980506));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, 2019, 385082142, 856980506));
    rowList.add(Row.ofKind(RowKind.INSERT, 2018, 700059566, 700059566));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, 2018, 700059566, 700059566));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, 2018, 678815482, 700059566));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, 2018, 678815482, 700059566));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, 2018, 324512774, 700059566));
    rowList.add(Row.ofKind(RowKind.INSERT, 2017, 517218368, 517218368));
    rowList.add(Row.ofKind(RowKind.UPDATE_BEFORE, 2017, 517218368, 517218368));
    rowList.add(Row.ofKind(RowKind.UPDATE_AFTER, 2017, 412563408, 517218368));

    return rowList;
  }

}
