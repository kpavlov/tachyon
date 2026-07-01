/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

/** Tracks whether an operation has been cancelled. */
public interface Cancellation {

    /** Returns {@code true} if cancellation has been requested. */
    boolean isCancelled();

    /** Throws {@link CancellationException} if cancellation has been requested. */
    void throwIfCancelled() throws CancellationException;
}
