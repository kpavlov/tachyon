/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.runtime;

import dev.tachyonmcp.api.annotations.InternalApi;

/**
 * Represents SSE stream backpressure state.
 */
@InternalApi
public enum Backpressure {
    /** Stream is writable. */
    HOT,
    /** Stream is not writable. */
    COLD
}
