package io.confluent.developer;

import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TestUtils {

    public static LocalDateTime dateTime(String dateString, DateTimeFormatter formatter){
        return LocalDateTime.parse(dateString, formatter);
    }

    public static LocalDateTime yyyy_MM_dd(String dateString) {
        return dateTime(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static List<Row> rowObjectsFromTableResult(TableResult tableResult) throws Exception {
        try(CloseableIterator<Row> closeableIterator = tableResult.collect()) {
            List<Row> rows = new ArrayList<>();
            while (closeableIterator.hasNext()) {
                rows.add(closeableIterator.next());
            }
            return rows;
        }
    }
}
