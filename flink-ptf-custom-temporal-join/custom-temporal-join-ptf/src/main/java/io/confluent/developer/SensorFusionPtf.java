package io.confluent.developer;

import com.google.common.math.Quantiles;
import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.table.annotation.ArgumentTrait.REQUIRE_ON_TIME;
import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

/**
 * Fuses a temperature reading stream and a humidity reading stream, correlated by machine/sensor
 * ID, into a single maintenance verdict per tumbling window: {@code needsMaintenance} is
 * {@code true} when the window's median temperature exceeds {@code temperatureThreshold} and its
 * median humidity exceeds {@code humidityThreshold}.
 *
 * <p>Unlike the {@code IncrementalOutputWindow} PTF (see the {@code flink-ptf-incremental-windowing}
 * tutorial), which re-emits its running aggregate on every event, this PTF stays silent while a
 * window is in progress and emits exactly once per window -- when a later reading, for either
 * input table, rolls the window forward.
 *
 * <p>Because temperature and humidity readings arrive on two independent set-semantic tables, this
 * PTF also demonstrates a custom temporal join: {@code eval} receives a non-null row from whichever
 * table just produced an event, with the other table argument left {@code null}, and the Flink
 * runtime co-locates rows across both tables that share the same {@code PARTITION BY} key.
 */
public class SensorFusionPtf extends ProcessTableFunction<SensorFusionPtf.MaintenanceResult> {

    // Emitted once per window, only when a later reading rolls the window forward.
    public static class MaintenanceResult {
        public LocalDateTime windowStart;
        public LocalDateTime windowEnd;
        public boolean needsMaintenance;
    }

    // Partitioned state tracking the in-progress window for a machine/sensor pair.
    public static class SensorFusionState {
        public long windowStartMillis = -1L;
        public long windowEndMillis = -1L;
        public List<Double> temperatures = new ArrayList<>();
        public List<Double> humidities = new ArrayList<>();
    }

    public void eval(
            Context ctx,
            @StateHint SensorFusionState state,
            @ArgumentHint(name = "temperature_reading", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row temperatureReading,
            @ArgumentHint(name = "humidity_reading", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row humidityReading,
            @ArgumentHint(name = "windowInterval") Duration windowInterval,
            @ArgumentHint(name = "temperatureThreshold") double temperatureThreshold,
            @ArgumentHint(name = "humidityThreshold") double humidityThreshold
    ) {
        TimeContext<Instant> timeCtx = ctx.timeContext(Instant.class);
        long currentEventTime = timeCtx.time().toEpochMilli();

        long intervalMillis = windowInterval.toMillis();
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("windowInterval must be a positive duration");
        }
        long windowStart = currentEventTime - (currentEventTime % intervalMillis);
        long windowEnd = windowStart + intervalMillis;

        // Check if we've moved to a new window - if so, emit the completed window's
        // maintenance verdict before resetting state
        if (state.windowStartMillis != -1L && state.windowStartMillis != windowStart) {
            emitWindowResult(state, temperatureThreshold, humidityThreshold);
            state.temperatures.clear();
            state.humidities.clear();
        }

        state.windowStartMillis = windowStart;
        state.windowEndMillis = windowEnd;

        // Aggregate the current reading, from whichever table produced it
        if (temperatureReading != null) {
            Double temperature = temperatureReading.getFieldAs("temperature");
            if (temperature != null) {
                state.temperatures.add(temperature);
            }
        } else if (humidityReading != null) {
            Double humidity = humidityReading.getFieldAs("humidity");
            if (humidity != null) {
                state.humidities.add(humidity);
            }
        }
    }

    // Helper: emit the completed window's maintenance verdict
    private void emitWindowResult(SensorFusionState state, double temperatureThreshold, double humidityThreshold) {
        if (state.temperatures.isEmpty() || state.humidities.isEmpty()) {
            return;
        }
        double medianTemperature = Quantiles.median().compute(state.temperatures);
        double medianHumidity = Quantiles.median().compute(state.humidities);

        MaintenanceResult result = new MaintenanceResult();
        result.windowStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(state.windowStartMillis), ZoneOffset.UTC);
        result.windowEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(state.windowEndMillis), ZoneOffset.UTC);
        result.needsMaintenance = medianTemperature > temperatureThreshold && medianHumidity > humidityThreshold;
        collect(result);
    }
}
