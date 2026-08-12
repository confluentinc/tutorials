package io.confluent.developer;

import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.functions.ChangelogFunction;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;

import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

/**
 * Tracks each order's current status as a small self-correcting materialized view, keyed by
 * {@code order_id}. Given a stream of order lifecycle events ({@code PLACED}, {@code FULFILLED},
 * {@code SHIPPED}, {@code DELIVERED}, {@code CANCELED}), this PTF maps each event onto one of
 * three statuses ({@code PENDING}, {@code SHIPPED}, {@code DELIVERED}) and emits a changelog row
 * for the order whenever its status changes: {@code +I} for a brand-new order,
 * {@code +U} when its status advances, and {@code -D} when the order is canceled and should
 * disappear from the downstream view entirely.
 */
@DataTypeHint("ROW<status STRING>")
public class OrderStatusTracker extends ProcessTableFunction<Row> implements ChangelogFunction {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SHIPPED = "SHIPPED";
    private static final String STATUS_DELIVERED = "DELIVERED";

    // Partitioned state: the status last emitted downstream for this order_id, or null if no
    // row has been emitted yet.
    public static class OrderState {
        public String status;
    }

    @Override
    public ChangelogMode getChangelogMode(ChangelogContext changelogContext) {
        return ChangelogMode.upsert(false);
    }

    public void eval(
            Context ctx,
            @StateHint OrderState state,
            @ArgumentHint(name = "input", value = SET_SEMANTIC_TABLE) Row input
    ) {
        // order_id itself isn't part of this function's own output row -- the PARTITION BY
        // key is already prepended to every output row automatically.
        String eventType = input.getFieldAs("event_type");

        if ("CANCELED".equals(eventType)) {
            // Remove the order from the downstream view entirely.
            if (state.status != null) {
                collect(Row.ofKind(RowKind.DELETE, state.status));
            }
            ctx.clearAll();
            return;
        }

        String newStatus = toStatus(eventType);
        if (newStatus == null || newStatus.equals(state.status)) {
            // Unknown event type, or one that maps to the order's current status
            // (e.g. FULFILLED after PLACED): nothing changed, so emit nothing.
            return;
        }

        if (state.status == null) {
            collect(Row.ofKind(RowKind.INSERT, newStatus));
        } else {
            collect(Row.ofKind(RowKind.UPDATE_AFTER, newStatus));
        }
        state.status = newStatus;
    }

    private static String toStatus(String eventType) {
        switch (eventType) {
            case "PLACED":
            case "FULFILLED":
                return STATUS_PENDING;
            case "SHIPPED":
                return STATUS_SHIPPED;
            case "DELIVERED":
                return STATUS_DELIVERED;
            default:
                return null;
        }
    }
}
