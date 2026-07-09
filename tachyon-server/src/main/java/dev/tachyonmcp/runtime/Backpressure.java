/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.InternalApi;

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
