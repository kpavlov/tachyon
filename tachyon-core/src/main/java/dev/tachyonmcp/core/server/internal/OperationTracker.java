/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Tracks admitted requests until their complete dispatch pipeline terminates. */
public final class OperationTracker {
    private boolean closing;
    private int active;

    /**
     * Admits an operation unless shutdown has started, then tracks dispatch and transport
     * completion under that one admission decision.
     */
    public <T> CompletableFuture<T> execute(
            Supplier<CompletableFuture<T>> operation, CompletableFuture<Void> transportCompletion) {
        synchronized (this) {
            if (closing) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("Server is shutting down"));
            }
            active++;
        }
        try {
            final var result = operation.get();
            CompletableFuture.allOf(result, transportCompletion).whenComplete((value, failure) -> finished());
            return result;
        } catch (Throwable failure) {
            finished();
            throw failure;
        }
    }

    private synchronized void finished() {
        active--;
        if (closing) notifyAll();
    }

    /** Stops admission and waits for active operations using the shared shutdown deadline. */
    public synchronized void drain(long deadline) throws InterruptedException {
        closing = true;
        while (active != 0) {
            final var remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
    }
}
