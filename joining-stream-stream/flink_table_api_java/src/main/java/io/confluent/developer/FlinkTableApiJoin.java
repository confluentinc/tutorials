package io.confluent.developer;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Slide;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import io.confluent.flink.plugin.ConfluentSettings;
import io.confluent.flink.plugin.ConfluentTools;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.and;
import static org.apache.flink.table.api.Expressions.lit;

public class FlinkTableApiJoin {

    public static void main(String[] args) throws Exception {
        EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
        TableEnvironment tableEnv = TableEnvironment.create(envSettings);

        tableEnv.useCatalog("examples");
        tableEnv.useDatabase("marketplace");

        Table ordersTable = tableEnv.from("examples.marketplace.orders")
                .select(
                        $("order_id"),
                        $("customer_id").as("order_customer_id"),
                        $("product_id"),
                        $("price"),
                        $("$rowtime").as("order_time")
                );
        Table customersTable = tableEnv.from("examples.marketplace.customers")
                .select(
                        $("customer_id"),
                        $("name"),
                        $("$rowtime").as("customer_time")
                );

        TableResult tableResult = ordersTable.join(customersTable)
                .where(
                        and(
                                $("order_customer_id").isEqual($("customer_id")),
                                $("order_time").isGreaterOrEqual($("customer_time"))
                        )
                )
                .select(
                        $("order_id"),
                        $("product_id"),
                        $("name"),
                        $("order_time"),
                        $("customer_time")
                )
                .execute();

        // option 1: use ConfluentTools.printMaterialized or ConfluentTools.collectMaterialized
        ConfluentTools.printMaterialized(tableResult, 5);

        // option 2: use TableResult.collect
        try (CloseableIterator<Row> it = tableResult.collect()) {
            if (it.hasNext()) {
                Row row = it.next();
                System.out.printf("Order %s, Name %s, Order time %s",
                        row.getField("order_id"), row.getField("name"), row.getField("order_time"));
            }
        }
    }
}
