/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.InternalApi;

/**
 * Tracks whether an operation has been cancelled.
 */
@InternalApi
public interface Cancellation {

    /**
     * Returns {@code true} if cancellation has been requested.
     */
    boolean isCancelled();
}
