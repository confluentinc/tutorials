package io.confluent.developer;

import com.google.common.math.Quantiles;
import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

public class Median extends ProcessTableFunction<Median.MedianResult> {
    public static class TempsState {
        public List<Double> temps = new ArrayList<>();
    }

    // Median calculation POJO
    public static class MedianResult {
        public double temperature;
        public double median;

        public static MedianResult of(double temperature, double median) {
            MedianResult result = new MedianResult();
            result.temperature = temperature;
            result.median = median;
            return result;
        }
    }

    public void eval(
            @StateHint TempsState trailingTemps,
            @ArgumentHint(name = "input", value = SET_SEMANTIC_TABLE) Row input,
            @ArgumentHint(name = "numTrailing") int numTrailing
    ) {
        Double temperature = input.getFieldAs("temperature");

        trailingTemps.temps.add(temperature);
        while (trailingTemps.temps.size() > numTrailing) {
            trailingTemps.temps.remove(0);
        }

        collect(MedianResult.of(temperature, Quantiles.median().compute(trailingTemps.temps)));
    }
}
