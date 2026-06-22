/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires a log entry at configurable thresholds when a handler takes longer than expected.
 *
 * <p>Log level is chosen by the handler thread's state at the moment the watchdog fires:
 *
 * <ul>
 *   <li>{@code BLOCKED} → WARN (waiting on a monitor — lock contention or deadlock)
 *   <li>{@code RUNNABLE} → DEBUG (long computation or hidden blocking)
 *   <li>{@code WAITING} / {@code TIMED_WAITING} → DEBUG (virtual thread parked — expected for
 *       async I/O)
 * </ul>
 */
public final class HandlerWatchdog {

    private static final Logger logger = LoggerFactory.getLogger(HandlerWatchdog.class);

    // Single daemon platform thread — scheduling infrastructure, not application I/O.
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "handler-watchdog");
        t.setDaemon(true);
        return t;
    });

    private static final String MSG =
            "Handler slow: method={}, id={}, elapsed={}ms, state={}, thread={}#{} virtual={}{}";

    private HandlerWatchdog() {}

    /**
     * Schedules a watchdog that fires after {@code delayMs} milliseconds and logs the live stack
     * trace of {@code handlerThread} at a level determined by its state. Cancel the returned
     * {@link Future} (with {@code cancel(false)}) when the handler completes normally.
     */
    public static Future<?> watch(Object method, Object id, long startNs, Thread handlerThread, long delayMs) {
        return SCHEDULER.schedule(() -> fire(method, id, startNs, handlerThread), delayMs, TimeUnit.MILLISECONDS);
    }

    private static void fire(Object method, Object id, long startNs, Thread thread) {
        var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        var state = thread.getState();
        var frames = thread.getStackTrace();
        var sb = new StringBuilder();
        for (var f : frames) sb.append("\n    at ").append(f);
        switch (state) {
            case BLOCKED ->
                logger.warn(
                        MSG, method, id, elapsedMs, state, thread.getName(), thread.threadId(), thread.isVirtual(), sb);
            case RUNNABLE ->
                logger.debug(
                        MSG, method, id, elapsedMs, state, thread.getName(), thread.threadId(), thread.isVirtual(), sb);
            default ->
                logger.debug(
                        MSG, method, id, elapsedMs, state, thread.getName(), thread.threadId(), thread.isVirtual(), sb);
        }
    }
}
