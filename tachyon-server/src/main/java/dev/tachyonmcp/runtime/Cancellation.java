/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

public interface Cancellation {

    boolean isCancelled();

    void throwIfCancelled() throws CancellationException;
}
