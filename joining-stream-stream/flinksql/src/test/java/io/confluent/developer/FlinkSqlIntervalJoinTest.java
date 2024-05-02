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

public class FlinkSqlIntervalJoinTest extends AbstractFlinkKafkaTest {

  @Test
  public void testJoin() throws Exception {
    // create base orders and shipments tables, and populate with test data
    streamTableEnv.executeSql(getResourceFileContents("create-orders.sql.template",
            Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("create-shipments.sql.template",
            Optional.of(kafkaPort),Optional.of(schemaRegistryPort))).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-orders.sql")).await();
    streamTableEnv.executeSql(getResourceFileContents("populate-shipments.sql")).await();

    // execute query on result table that should have joined shipments with orders
    TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-join-order-shipments.sql"));

    // Compare actual and expected results
    List<Row> actualResults = rowObjectsFromTableResult(tableResult);
    List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
    assertEquals(actualResults, expectedRowResults);
  }

  private List<Row> getExpectedFinalUpdateRowObjects() {
    List<Row> rowList = new ArrayList<>();
    rowList.add(Row.ofKind(RowKind.INSERT, 1,  "2023-08-23 13:36:15", 404.89, "Art Vandelay",    "shipment_1",  "2023-08-23 14:36:15", "Bar Harbor",     1));
    rowList.add(Row.ofKind(RowKind.INSERT, 2,  "2023-08-23 17:36:15", 50.45,  "Bob Sacamanto",   "shipment_2",  "2023-08-24 00:36:15", "Boston",         7));
    rowList.add(Row.ofKind(RowKind.INSERT, 3,  "2023-08-23 17:36:15", 113.23, "Bilbo Baggins",   "shipment_3",  "2023-08-24 00:36:15", "Providence",     7));
    rowList.add(Row.ofKind(RowKind.INSERT, 4,  "2023-08-23 13:36:15", 90.43,  "Harry Potter",    "shipment_4",  "2023-08-23 17:36:15", "Springfield",    4));
    rowList.add(Row.ofKind(RowKind.INSERT, 5,  "2023-08-23 15:36:15", 495.22, "John Hechinger",  "shipment_5",  "2023-08-23 16:36:15", "Bar Harbor",     1));
    rowList.add(Row.ofKind(RowKind.INSERT, 6,  "2023-08-23 17:36:15", 410.13, "Mandelorean",     "shipment_6",  "2023-08-24 00:36:15", "Boston",         7));
    rowList.add(Row.ofKind(RowKind.INSERT, 7,  "2023-08-23 16:36:15", 333.84, "Jane Smith",      "shipment_7",  "2023-08-23 21:36:15", "Jackson Hole",   5));
    rowList.add(Row.ofKind(RowKind.INSERT, 8,  "2023-08-23 15:36:15", 26.14,  "HJ Pennypacker",  "shipment_8",  "2023-08-23 16:36:15", "Whitefish",      1));
    rowList.add(Row.ofKind(RowKind.INSERT, 9,  "2023-08-23 13:36:15", 450.77, "Colonel Mustard", "shipment_9",  "2023-08-25 13:36:15", "Jackson Hole",   48));
    rowList.add(Row.ofKind(RowKind.INSERT, 10, "2023-08-23 16:36:15", 195.13, "Prof. Jones",     "shipment_10", "2023-08-25 13:36:15", "Columbia Falls", 45));
    return rowList;
  }

}
