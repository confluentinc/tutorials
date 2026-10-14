package io.confluent.developer;

import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

/**
 * Maintains live statistics per stock ticker over the most recent N trades, emitting the updated
 * statistics on every incoming trade.
 *
 * <p>The statistics are maintained incrementally: each trade adds its contribution to running
 * totals (volume, price sum, buy count, sell count), and once more than N trades are retained the
 * oldest trade subtracts its contribution. Nothing is recomputed from scratch on each trade, with
 * one exception: the min and max price are rescanned over the retained trades only when the trade
 * that was just dropped was the current min or max.
 *
 * <p>The window is count-based, so it needs no event time or timers: "oldest" means the earliest
 * trade to arrive at the PTF for that ticker. Until a ticker has seen N trades, the statistics
 * cover the trades seen so far.
 */
public class LastNTradesStats extends ProcessTableFunction<LastNTradesStats.Stats> {

    // Emitted on every trade (the ticker is added automatically by PARTITION BY).
    public static class Stats {
        public long totalVolume;
        public double avgPrice;
        public double minPrice;
        public double maxPrice;
        public long numSellOrders;
        public long numBuyOrders;
    }

    // A trade retained in state for as long as it is among the last N trades.
    public static class Trade {
        public long quantity;
        public double price;
        public boolean isSell;
    }

    // Partitioned state: the last N trades (oldest first) plus running aggregates over them.
    public static class TradesState {
        public List<Trade> trades = new ArrayList<>();
        public long totalVolume = 0L;
        public double priceSum = 0.0;
        public double minPrice = 0.0;
        public double maxPrice = 0.0;
        public long numSellOrders = 0L;
        public long numBuyOrders = 0L;
    }

    public void eval(
            Context ctx,
            @StateHint TradesState state,
            @ArgumentHint(name = "input", value = SET_SEMANTIC_TABLE) Row input,
            @ArgumentHint(name = "numTrades") Integer numTrades
    ) {
        if (numTrades == null || numTrades <= 0) {
            throw new IllegalArgumentException("numTrades must be a positive integer");
        }

        Number quantity = input.getFieldAs("quantity");
        Number price = input.getFieldAs("price");
        String side = input.getFieldAs("side");
        if (quantity == null || price == null || side == null) {
            return;
        }

        addTrade(state, quantity.longValue(), price.doubleValue(), "SELL".equalsIgnoreCase(side));
        // Drop the oldest trades until only the last N remain (more than one if numTrades was lowered)
        boolean extremeEvicted = false;
        while (state.trades.size() > numTrades) {
            extremeEvicted |= evictOldest(state);
        }
        // Only rescan for min/max if an evicted trade could have been the min or the max
        if (extremeEvicted) {
            rescanMinMax(state);
        }
        emitStats(state);
    }

    // Add the new trade's contribution to the running aggregates.
    private void addTrade(TradesState state, long quantity, double price, boolean isSell) {
        Trade trade = new Trade();
        trade.quantity = quantity;
        trade.price = price;
        trade.isSell = isSell;

        if (state.trades.isEmpty()) {
            state.minPrice = price;
            state.maxPrice = price;
        } else {
            state.minPrice = Math.min(state.minPrice, price);
            state.maxPrice = Math.max(state.maxPrice, price);
        }

        state.trades.add(trade);
        state.totalVolume += quantity;
        state.priceSum += price;
        if (isSell) {
            state.numSellOrders++;
        } else {
            state.numBuyOrders++;
        }
    }

    // Subtract the oldest trade from the running aggregates. Returns true if it was the min or max.
    private boolean evictOldest(TradesState state) {
        Trade oldest = state.trades.remove(0);
        state.totalVolume -= oldest.quantity;
        state.priceSum -= oldest.price;
        if (oldest.isSell) {
            state.numSellOrders--;
        } else {
            state.numBuyOrders--;
        }
        return oldest.price == state.minPrice || oldest.price == state.maxPrice;
    }

    private void rescanMinMax(TradesState state) {
        state.minPrice = Double.MAX_VALUE;
        state.maxPrice = -Double.MAX_VALUE;
        for (Trade t : state.trades) {
            state.minPrice = Math.min(state.minPrice, t.price);
            state.maxPrice = Math.max(state.maxPrice, t.price);
        }
    }

    private void emitStats(TradesState state) {
        Stats stats = new Stats();
        stats.totalVolume = state.totalVolume;
        stats.avgPrice = state.priceSum / state.trades.size();
        stats.minPrice = state.minPrice;
        stats.maxPrice = state.maxPrice;
        stats.numSellOrders = state.numSellOrders;
        stats.numBuyOrders = state.numBuyOrders;
        collect(stats);
    }
}
