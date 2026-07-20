/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.internal;

import dev.tachyonmcp.annotations.InternalApi;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared periodic-sweep scheduling for background cleanup tasks (session eviction, task
 * expiry/retention). A single daemon thread runs {@link #sweep()} at a fixed delay; exceptions
 * from a sweep are caught and logged so one failed pass doesn't cancel future runs.
 */
@InternalApi
public abstract class AbstractJanitor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(AbstractJanitor.class);

    private final String threadName;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile @Nullable ScheduledExecutorService executor;

    protected AbstractJanitor(String threadName) {
        this.threadName = threadName;
    }

    /** Starts the periodic sweep at the given interval. Idempotent — later calls no-op. */
    public final void start(Duration interval) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        var exec = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
        executor = exec;
        var intervalMs = interval.toMillis();
        exec.scheduleWithFixedDelay(this::safeSweep, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void safeSweep() {
        try {
            sweep();
        } catch (Exception e) {
            logger.warn("Janitor sweep failed", e);
        }
    }

    /** One janitor pass. */
    protected abstract void sweep();

    @Override
    public void close() {
        var exec = executor;
        if (exec != null) {
            exec.shutdownNow();
            executor = null;
        }
    }
}
