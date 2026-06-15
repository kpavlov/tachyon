/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import org.jspecify.annotations.Nullable;

public sealed interface InteractionEvent {

    /** INITIALIZATION → OPERATION: protocol negotiation complete. */
    record OperationStarted(@Nullable Session session) implements InteractionEvent {
        /** Reuse for stateless connections (no session). */
        public static final OperationStarted STATELESS = new OperationStarted(null);
    }

    /** OPERATION → SHUTDOWN: client requested graceful termination. */
    record ShutdownStarted(@Nullable String sessionId) implements InteractionEvent {}

    /** SHUTDOWN complete: all cleanup done, channel may be closed. */
    record ShutdownComplete() implements InteractionEvent {
        public static final ShutdownComplete INSTANCE = new ShutdownComplete();
    }
}
