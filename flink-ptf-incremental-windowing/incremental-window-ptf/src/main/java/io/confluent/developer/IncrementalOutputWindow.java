package io.confluent.developer;

import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.apache.flink.table.annotation.ArgumentTrait.REQUIRE_ON_TIME;
import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

/**
 * Computes a running trade count and average price per stock ticker within a tumbling window,
 * emitting the updated aggregate on every incoming trade instead of waiting for the window to
 * close.
 *
 * <p>Every emission is marked {@code isFinal = false}, with one exception: when a trade's
 * timestamp falls into a new window, the PTF first re-emits the just-completed window's aggregate
 * one last time with {@code isFinal = true}, then resets its state and starts aggregating the new
 * window.
 *
 * <p>Unlike the {@code SessionWindow} PTF (see the {@code flink-ptf-absence-detection} tutorial),
 * this PTF registers no event-time timers: every state transition is driven by comparing the
 * arriving event's window boundary against the previously stored one, so results appear as soon
 * as -- and only when -- trades arrive.
 */
public class IncrementalOutputWindow extends ProcessTableFunction<IncrementalOutputWindow.WindowResult> {

    // Emitted on every trade; the last emission before a window closes has isFinal = true.
    public static class WindowResult {
        public String ticker;
        public LocalDateTime windowStart;
        public LocalDateTime windowEnd;
        public int numTrades;
        public double avgPrice;
        public boolean isFinal;
    }

    // Partitioned state tracking the in-progress window for a ticker.
    public static class WindowState {
        public String ticker;
        public long windowStartMillis = -1L;
        public long windowEndMillis = -1L;
        public int count = 0;
        public double priceSum = 0.0;
    }

    public void eval(
            Context ctx,
            @StateHint WindowState state,
            @ArgumentHint(name = "input", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row input,
            @ArgumentHint(name = "windowInterval") Duration windowInterval
    ) {
        TimeContext<Instant> timeCtx = ctx.timeContext(Instant.class);
        long currentEventTime = timeCtx.time().toEpochMilli();
        String ticker = input.getFieldAs("ticker");
        Double price = input.getFieldAs("price");

        long intervalMillis = windowInterval.toMillis();
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("windowInterval must be a positive duration");
        }
        long windowStart = currentEventTime - (currentEventTime % intervalMillis);
        long windowEnd = windowStart + intervalMillis;

        // Check if we've moved to a new window - if so, reset state, but first emit the
        // completed window's result one final time
        if (state.windowStartMillis != -1L && state.windowStartMillis != windowStart) {
            emitWindowResult(state, true);
            state.count = 0;
            state.priceSum = 0.0;
        }

        state.ticker = ticker;
        state.windowStartMillis = windowStart;
        state.windowEndMillis = windowEnd;

        // Aggregate the current trade
        state.count++;
        if (price != null) {
            state.priceSum += price;
        }

        // Immediately emit the current window result
        emitWindowResult(state, false);
    }

    // Helper: emit the window's current running aggregate
    private void emitWindowResult(WindowState state, boolean isFinal) {
        WindowResult result = new WindowResult();
        result.ticker = state.ticker;
        result.windowStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(state.windowStartMillis), ZoneOffset.UTC);
        result.windowEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(state.windowEndMillis), ZoneOffset.UTC);
        result.numTrades = state.count;
        result.avgPrice = state.priceSum / state.count;
        result.isFinal = isFinal;
        collect(result);
    }
}
