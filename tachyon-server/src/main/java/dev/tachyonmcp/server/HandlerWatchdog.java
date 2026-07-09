/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.annotations.InternalApi;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires a log entry at configurable thresholds when a handler takes longer than expected.
 * Does not capture a thread reference — suitable for async handler pipelines where the
 * continuation may run on any thread.
 */
@InternalApi
public final class HandlerWatchdog {

    private static final Logger logger = LoggerFactory.getLogger(HandlerWatchdog.class);

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "handler-watchdog");
        t.setDaemon(true);
        return t;
    });

    private static final Future<?> NOOP = CompletableFuture.completedFuture(null);

    private HandlerWatchdog() {}

    /**
     * Schedules a watchdog that fires after {@code delayMs} milliseconds and logs a warning.
     * Cancel the returned {@link Future} (with {@code cancel(false)}) when the handler completes normally.
     * When debug logging is disabled the output can never be seen, so no timer is scheduled at
     * all — avoids per-request schedule/cancel churn on the single timer thread.
     */
    public static Future<?> watch(Object method, Object id, long startNs, long delayMs) {
        if (!logger.isDebugEnabled()) {
            return NOOP;
        }
        return SCHEDULER.schedule(() -> fire(method, id, startNs), delayMs, TimeUnit.MILLISECONDS);
    }

    private static void fire(Object method, Object id, long startNs) {
        var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        logger.debug("Handler slow: method={}, id={}, elapsed={}ms", method, id, elapsedMs);
    }
}
