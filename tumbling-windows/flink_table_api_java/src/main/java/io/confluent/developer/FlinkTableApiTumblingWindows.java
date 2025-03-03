package io.confluent.developer;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.Tumble;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import io.confluent.flink.plugin.ConfluentSettings;
import io.confluent.flink.plugin.ConfluentTools;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.lit;

public class FlinkTableApiTumblingWindows {

    public static void main(String[] args) throws Exception {
        EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
        TableEnvironment tableEnv = TableEnvironment.create(envSettings);

        tableEnv.useCatalog("examples");
        tableEnv.useDatabase("marketplace");
        TableResult tableResult = tableEnv.from("examples.marketplace.orders")
                .window(
                        Tumble.over(lit(2).seconds())
                                .on($("$rowtime"))
                                .as("window")
                ).groupBy(
                        $("window")
                ).select(
                        $("customer_id").count().as("count"),
                        $("window").start().as("window_start"),
                        $("window").end().as("window_end")
                )
                .execute();
        // option 1: use ConfluentTools.printMaterialized or ConfluentTools.collectMaterialized
        ConfluentTools.printMaterialized(tableResult, 2);

        // option 2: use TableResult.collect
        try (CloseableIterator<Row> it = tableResult.collect()) {
            if (it.hasNext()) {
                Row row = it.next();
                System.out.println(row.getField("count"));
                System.out.println(row.getField("window_start"));
                System.out.println(row.getField("window_end"));

            }
        }
    }
}
