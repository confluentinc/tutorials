package io.confluent.developer;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Over;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import io.confluent.flink.plugin.ConfluentSettings;
import io.confluent.flink.plugin.ConfluentTools;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.CURRENT_ROW;
import static org.apache.flink.table.api.Expressions.lit;
import static org.apache.flink.table.api.Expressions.rowInterval;

public class FlinkTableApiOverAggregations {

    public static void main(String[] args) throws Exception {
        EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
        TableEnvironment tableEnv = TableEnvironment.create(envSettings);

        tableEnv.useCatalog("examples");
        tableEnv.useDatabase("marketplace");
        TableResult tableResult = tableEnv.from("examples.marketplace.orders")
                .window(
                        Over.orderBy($("$rowtime"))
                                .preceding(rowInterval(5L))
                                .following(CURRENT_ROW)
                                .as("window"))
                .select(
                        $("price"),
                        $("price")
                                .avg()
                                .over($("window"))
                                .round(lit(2))
                                .as("rolling_avg_price")
                )
                .execute();


        // option 1: use ConfluentTools.printMaterialized or ConfluentTools.collectMaterialized
        ConfluentTools.printMaterialized(tableResult, 10);

        // option 2: use TableResult.collect
        try (CloseableIterator<Row> it = tableResult.collect()) {
            if (it.hasNext()) {
                Row row = it.next();
                System.out.println(row.getField("price"));
                System.out.println(row.getField("rolling_avg_price"));
            }
        }
    }
}
