/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

/**
 * Tracks whether an operation has been cancelled.
 */
public interface Cancellation {

    /**
     * Returns {@code true} if cancellation has been requested.
     */
    boolean isCancelled();
}
