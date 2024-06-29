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

public class FlinkSqlSessionWindowsTest extends AbstractFlinkKafkaTest {

    @Test
    public void testSessionWindows() throws Exception {
        streamTableEnv.executeSql(getResourceFileContents("create-clicks-table.sql.template",
                Optional.of(kafkaPort), Optional.of(schemaRegistryPort))).await();
        streamTableEnv.executeSql(getResourceFileContents("populate-clicks.sql")).await();

        TableResult tableResult = streamTableEnv.executeSql(getResourceFileContents("query-click-sessions.sql"));
        List<Row> actualResults = rowObjectsFromTableResult(tableResult);
        List<Row> expectedRowResults = getExpectedFinalUpdateRowObjects();
        assertEquals(actualResults, expectedRowResults);
    }

    private List<Row> getExpectedFinalUpdateRowObjects() {
        List<Row> rowList = new ArrayList<>();
        rowList.add(Row.ofKind(RowKind.INSERT, "/acme/jeep-stuff/", 3L, yyyy_MM_dd("2023-07-09 01:00:00"), yyyy_MM_dd("2023-07-09 01:03:00")));
        rowList.add(Row.ofKind(RowKind.INSERT, "/farm-for-all/chickens/", 3L, yyyy_MM_dd("2023-07-09 02:00:10"), yyyy_MM_dd("2023-07-09 02:03:00")));
        rowList.add(Row.ofKind(RowKind.INSERT, "/farm-for-all/tractors/", 1L, yyyy_MM_dd("2023-07-09 02:30:00"), yyyy_MM_dd("2023-07-09 02:32:00")));
        rowList.add(Row.ofKind(RowKind.INSERT, "/amc-rio/movies/", 2L, yyyy_MM_dd("2023-07-09 09:00:00"), yyyy_MM_dd("2023-07-09 09:02:30")));
        rowList.add(Row.ofKind(RowKind.INSERT, "/trips/packages/", 1L, yyyy_MM_dd("2023-07-09 12:00:00"), yyyy_MM_dd("2023-07-09 12:02:00")));
        rowList.add(Row.ofKind(RowKind.INSERT, "/farm-for-all/tractors/", 1L, yyyy_MM_dd("2023-07-10 02:31:00"), yyyy_MM_dd("2023-07-10 02:33:00")));

        return rowList;
    }
}
