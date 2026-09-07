/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.observability;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.ServerError;
import org.jspecify.annotations.Nullable;

/**
 * The terminal fact reported for one {@link OperationInfo}, resolved by the dispatcher from
 * classification it already computed (a {@link ServerError}'s resolved wire code, a task
 * registry publish, etc.) — never re-derived by the listener.
 */
@InternalApi
public sealed interface OperationOutcome {

    /** Rejected before any handler ran (unknown method, bad session state, malformed params, ...). */
    record Rejected(@Nullable ServerError error, int httpStatus) implements OperationOutcome {}

    /** The handler ran and its result was encoded onto the wire. */
    record Completed(@Nullable CapturedPayload responsePayload) implements OperationOutcome {}

    /** The handler produced a result but encoding it failed; the client received a fallback error. */
    record SerializationFailed(Throwable cause) implements OperationOutcome {}

    /**
     * The operation ended in a JSON-RPC error after a handler ran. {@code cause} is the throwable
     * that produced it, or {@code null} when the handler returned a {@link ServerError} value
     * directly rather than throwing/failing.
     */
    record HandlerFailed(ServerError error, int wireCode, @Nullable Throwable cause) implements OperationOutcome {}

    /** The handler's work was cancelled (client-initiated {@code notifications/cancelled} or similar). */
    record Cancelled() implements OperationOutcome {}

    /** A {@code tools/call} was handed off to an out-of-band task rather than returning inline. */
    record TaskHandoff(String taskId) implements OperationOutcome {}

    /** A recognized notification was handled ({@code notifications/initialized}, {@code .../cancelled}). */
    record NotificationAccepted() implements OperationOutcome {}

    /** A notification with no matching handler was silently accepted per protocol. */
    record NotificationIgnored() implements OperationOutcome {}

    /** A {@code subscriptions/listen} SSE stream was established; its stream lifetime is tracked separately. */
    record StreamEstablished() implements OperationOutcome {}
}
