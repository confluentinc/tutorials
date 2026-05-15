package io.confluent.developer;

import io.confluent.flink.plugin.ConfluentSettings;
import io.confluent.flink.plugin.ConfluentTools;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.lit;

public class TableApiPtfConfluentCloud {

    public static void main(String[] args) throws Exception {
        EnvironmentSettings envSettings = ConfluentSettings.fromResource("/cloud.properties");
        TableEnvironment tableEnv = TableEnvironment.create(envSettings);

        tableEnv.useCatalog("flink_table_api_tutorials_environment");
        tableEnv.useDatabase("flink_table_api_tutorials_cluster");

        TableResult tableResult = tableEnv.from("temperature_readings")
                .partitionBy($("sensor_id"))
                .process(Median.class,
                        lit(3).asArgument("numTrailing"))
                .execute();

        try (CloseableIterator<Row> it = tableResult.collect()) {
            for (int i = 0; it.hasNext() && i < 5; i++) {
                Row row = it.next();
                System.out.println("Current temp: " + row.getField("temperature") +
                        ", median over last 3: " + row.getField("median"));            }
        }
    }
}
