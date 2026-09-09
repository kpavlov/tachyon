/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.runtime;

import dev.tachyonmcp.api.annotations.ExperimentalApi;

/** Lifecycle states for a session. */
@ExperimentalApi(since = "1.0.0-beta.26")
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
