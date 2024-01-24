package io.confluent.developer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.kafka.common.utils.Time;

import static org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MaxFailuresUncaughtExceptionHandlerTest {

    private MaxFailuresUncaughtExceptionHandler exceptionHandler;
    private final IllegalStateException worksOnMyBoxException = new IllegalStateException("Strange, It worked on my box");
    Time time = Time.SYSTEM;
    @BeforeEach
    public void setUp() {
        long maxTimeMillis = 100;
        int maxFailures = 2;
        exceptionHandler = new MaxFailuresUncaughtExceptionHandler(maxFailures, maxTimeMillis);
    }

    @Test
    void shouldReplaceThreadWhenErrorsNotWithinMaxTime() {
        for (int i = 0; i < 10; i++) {
            assertEquals(REPLACE_THREAD, exceptionHandler.handle(worksOnMyBoxException));
            time.sleep(200);
        }
    }

    @Test
    void shouldShutdownApplicationWhenErrorsOccurWithinMaxTime() {
        assertEquals(REPLACE_THREAD, exceptionHandler.handle(worksOnMyBoxException));
        time.sleep(50);
        assertEquals(SHUTDOWN_APPLICATION, exceptionHandler.handle(worksOnMyBoxException));
    }
}