package io.confluent.developer;

import io.confluent.flink.plugin.ConfluentSettings;
import io.confluent.flink.plugin.ConfluentTools;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import static org.apache.flink.table.api.Expressions.$;

public class FlinkTableApiFiltering {

    public static void main(String[] args) throws Exception {
        EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
        TableEnvironment tableEnv = TableEnvironment.create(envSettings);

        TableResult tableResult = tableEnv.from("examples.marketplace.orders")
                .select($("customer_id"), $("product_id"), $("price"))
                .filter($("price").isGreaterOrEqual(50))
                .execute();

        // option 1: use ConfluentTools.printMaterialized or ConfluentTools.collectMaterialized
        ConfluentTools.printMaterialized(tableResult, 5);

        // option 2: use TableResult.collect
        try (CloseableIterator<Row> it = tableResult.collect()) {
            for (int i = 0; it.hasNext() && i < 5; i++) {
                Row row = it.next();
                System.out.println(row.getField("price"));
            }
        }
    }
}
