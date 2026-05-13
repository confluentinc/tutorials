package io.confluent.developer;

import com.google.common.math.Quantiles;
import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

@DataTypeHint("ROW<temperature DOUBLE, median DOUBLE>")
public class Median extends ProcessTableFunction<Row> {
    public static class TempsState {
        public List<Double> temps = new ArrayList<>();
    }

    public void eval(
            @StateHint TempsState trailingTemps,
            @ArgumentHint(SET_SEMANTIC_TABLE) Row row,
            @DataTypeHint("INT") Integer numTrailing
    ) {
        Double temperature = row.getFieldAs("temperature");

        trailingTemps.temps.add(temperature);
        while (trailingTemps.temps.size() > numTrailing) {
            trailingTemps.temps.remove(0);
        }

        collect(Row.of(temperature, Quantiles.median().compute(trailingTemps.temps)));
    }
}