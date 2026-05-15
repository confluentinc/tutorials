package io.confluent.developer;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.JobManagerOptions;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.lit;

public class TableApiPtfLocal {

    public static void main(String[] args) throws Exception {
        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .inStreamingMode()
                .build();

        TableEnvironment tEnv = TableEnvironment.create(settings);

        // setup
        tEnv.executeSql(
                """
                CREATE TABLE temperature_readings (
                        sensor_id INT,
                        temperature DOUBLE,
                        ts TIMESTAMP(3),
                        `partition` BIGINT METADATA VIRTUAL,
                        `offset` BIGINT METADATA VIRTUAL,
                        -- declare ts as event time attribute and use strictly ascending timestamp watermark strategy
                        WATERMARK FOR ts AS ts
                ) WITH (
                        'connector' = 'kafka',
                        'topic' = 'temperature-readings',
                        'properties.bootstrap.servers' = 'localhost:29092',
                        'scan.startup.mode' = 'earliest-offset',
                        'key.format' = 'raw',
                        'key.fields' = 'sensor_id',
                        'value.format' = 'avro-confluent',
                        'value.avro-confluent.url' = 'http://localhost:8081',
                        'value.fields-include' = 'EXCEPT_KEY'
                );"""
        );

        tEnv.executeSql(
                """
                INSERT INTO temperature_readings VALUES
                    (0, 55, TO_TIMESTAMP('2026-05-01 02:15:30')),
                    (0, 50, TO_TIMESTAMP('2026-05-01 02:20:30')),
                    (0, 45, TO_TIMESTAMP('2026-05-01 02:25:30')),
                    (0, 40, TO_TIMESTAMP('2026-05-01 02:30:30')),
                    (0, 45, TO_TIMESTAMP('2026-05-01 02:35:30')),
                    (0, 50, TO_TIMESTAMP('2026-05-01 02:40:30')),
                    (0, 55, TO_TIMESTAMP('2026-05-01 02:45:30')),
                    (0, 60, TO_TIMESTAMP('2026-05-01 02:50:30')),
                    (0, 60, TO_TIMESTAMP('2026-05-01 02:53:30'));"""
        ).await();

        TableResult tableResult = tEnv.from("temperature_readings")
                .partitionBy($("sensor_id"))
                .process(Median.class,
                        lit(3).asArgument("numTrailing"))
                .execute();

        try (CloseableIterator<Row> it = tableResult.collect()) {
            for (int i = 0; it.hasNext() && i < 5; i++) {
                Row row = it.next();
                System.out.println("Current temp: " + row.getField("temperature") +
                        ", median over last 3: " + row.getField("median"));
            }
        }
    }
}
