/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

/**
 * Represents SSE stream backpressure state.
 */
public enum Backpressure {
    /** Stream is writable. */
    HOT,
    /** Stream is not writable. */
    COLD
}
