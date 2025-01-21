package io.confluent.developer;


import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static io.confluent.developer.TestUtils.rowObjectsFromTableResult;
import static org.junit.Assert.assertEquals;

public class FlinkSqlMultiJoinTest extends AbstractFlinkKafkaTest {

  @Test
  public void testJoin() throws Exception {
    streamTableEnv.executeSql(getResourceFileContents("create-orders.sql.template",
            Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("create-products.sql.template",
            Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("create-customers.sql.template",
            Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-customers.sql")).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-products.sql")).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-orders.sql")).await();
    streamTableEnv.executeSql(getResourceFileContents("advance-customers.sql")).await();
    streamTableEnv.executeSql(getResourceFileContents("advance-products.sql")).await();

    // execute query on result table that should have joined shipments with orders
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-temporal-multi-join.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult, 3);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(new HashSet(expectedRowResults), new HashSet(actualResults));
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 1, "Phyllis Ackerman", "GripMax Tennis Shoes"));
    rowList.add(Row.ofKind(RowKind.INSERT, 2, "Janis Smithson", "Air Elite Sneakers"));
    rowList.add(Row.ofKind(RowKind.INSERT, 3, "William Schnaube", "GripMax Tennis Shoes"));
    return rowList;
  }

}
