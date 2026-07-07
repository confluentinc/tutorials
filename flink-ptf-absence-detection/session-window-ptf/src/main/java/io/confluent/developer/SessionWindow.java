package io.confluent.developer;

import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.table.annotation.ArgumentTrait.REQUIRE_ON_TIME;
import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

/**
 * Groups a stream of user events into sessions per user. A session ends either when the user sends
 * a {@code LOGOUT} event or after a configurable period of inactivity (the session timeout). On
 * either condition, the PTF emits a one-row summary of the session.
 *
 * <p>Whereas the {@code Median} PTF demonstrates managed state, this PTF additionally demonstrates
 * <b>event-time timers</b>: each event (re)registers a timeout timer, and {@code onTimer} fires
 * only if no further event arrives before the session timeout elapses.
 */
public class SessionWindow extends ProcessTableFunction<SessionWindow.SessionSummary> {

    // Summary emitted once per completed session.
    public static class SessionSummary {
        public String userId;
        public String status;          // COMPLETED (logout) or TIMEOUT (inactivity)
        public long durationMillis;
        public int eventCount;
        public String eventSequence;   // e.g. "LOGIN > VIEW > VIEW > LOGOUT"
    }

    // Partitioned state tracking the in-progress session for a user.
    public static class SessionState {
        public String userId = null;
        public Long sessionStartMillis = null;
        public int eventCount = 0;
    }

    public static class EventTypesState {
        public List<String> states = new ArrayList<>();
    }

    public void eval(
            Context ctx,
            @StateHint SessionState session,
            @StateHint EventTypesState eventTypes,
            @ArgumentHint(name = "input", value = {SET_SEMANTIC_TABLE, REQUIRE_ON_TIME}) Row input,
            @ArgumentHint(name = "sessionTimeout") int sessionTimeout
    ) throws Exception {
        TimeContext<Instant> timeCtx = ctx.timeContext(Instant.class);
        String userId = input.getFieldAs("user_id");
        String eventType = input.getFieldAs("event_type");

        if (session.sessionStartMillis == null) {
            session.userId = userId;
            session.sessionStartMillis = timeCtx.time().toEpochMilli();
        }
        session.eventCount++;
        eventTypes.states.add(eventType);

        // (Re)arm the inactivity timer. Registering "timeout" again replaces the prior timer, so it
        // only fires once the user has been idle for the full session timeout.
        timeCtx.registerOnTime("timeout", timeCtx.time().plus(Duration.ofSeconds(sessionTimeout)));

        if ("LOGOUT".equals(eventType)) {
            collect(summarize(session, eventTypes, "COMPLETED", timeCtx.time().toEpochMilli()));
            ctx.clearAll();
        }
    }

    public void onTimer(
            OnTimerContext onTimerCtx,
            SessionState session,
            EventTypesState eventTypes
    ) throws Exception {
        long now = onTimerCtx.timeContext(Instant.class).time().toEpochMilli();
        collect(summarize(session, eventTypes, "TIMEOUT", now));
        onTimerCtx.clearAll();
    }

    private SessionSummary summarize(
            SessionState session,
            EventTypesState eventTypes,
            String status,
            long endMillis
    ) throws Exception {
        SessionSummary summary = new SessionSummary();
        summary.userId = session.userId;
        summary.status = status;
        summary.durationMillis = endMillis - session.sessionStartMillis;
        summary.eventCount = session.eventCount;
        summary.eventSequence = String.join(" > ", eventTypes.states);
        return summary;
    }
}
