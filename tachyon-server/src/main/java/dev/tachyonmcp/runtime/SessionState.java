/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

/** Lifecycle states for a session. */
public enum SessionState {
    /** Session created, not yet initialized. */
    INITIALIZING,
    /** Session ready for normal operation. */
    ACTIVE,
    /** Session is draining — no new requests but in-flight ones complete. */
    DRAINING,
    /** Session terminated and resources released. */
    CLOSED
}
