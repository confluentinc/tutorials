package io.confluent.developer;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.*;

public class MaxFailuresUncaughtExceptionHandlerTest {

    private MaxFailuresUncaughtExceptionHandler exceptionHandler;
    private final IllegalStateException worksOnMyBoxException = new IllegalStateException("Strange, It worked on my box");

    @Before
    public void setUp() {
        long maxTimeMillis = 100;
        int maxFailures = 2;
        exceptionHandler = new MaxFailuresUncaughtExceptionHandler(maxFailures, maxTimeMillis);
    }

    @Test
    public void shouldReplaceThreadWhenErrorsNotWithinMaxTime() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(REPLACE_THREAD, exceptionHandler.handle(worksOnMyBoxException));
            Thread.sleep(200);
        }
    }

    @Test
    public void shouldShutdownApplicationWhenErrorsOccurWithinMaxTime() throws Exception {
        assertEquals(REPLACE_THREAD, exceptionHandler.handle(worksOnMyBoxException));
        Thread.sleep(50);
        assertEquals(SHUTDOWN_APPLICATION, exceptionHandler.handle(worksOnMyBoxException));
    }
}