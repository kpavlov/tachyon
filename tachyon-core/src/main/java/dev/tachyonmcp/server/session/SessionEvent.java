/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.domain.RequestId;
import org.jspecify.annotations.Nullable;

/** A recorded session event — request, response, notification, or cancellation. */
@InternalApi
public sealed interface SessionEvent {

    /** The session this event belongs to. */
    String sessionId();

    /** Timestamp of the event (epoch millis). */
    long timestamp();

    /** SSE event ID (for replay), or -1 if not assigned. */
    default long sseEventId() {
        return -1;
    }

    /**
     * Identifies the SSE stream the event was delivered on: a POST-SSE stream's key, or
     * {@code null} for the session's general-purpose GET stream. Replay after reconnection is
     * per-stream (MCP Streamable HTTP: the server MUST NOT replay messages that would have been
     * sent on a different stream), so each outbound event records its originating stream.
     */
    default @Nullable String streamKey() {
        return null;
    }

    /** An inbound request from the client. */
    record RequestEvent(String sessionId, RequestId requestId, String method, String paramsJson, long timestamp)
            implements SessionEvent {}

    /** An outbound (server-to-client) request. */
    record OutboundRequestEvent(
            String sessionId,
            RequestId requestId,
            String method,
            String paramsJson,
            long timestamp,
            long sseEventId,
            @Nullable String streamKey)
            implements SessionEvent {}

    /** A response sent to the client. */
    record ResponseEvent(
            String sessionId,
            RequestId requestId,
            String resultJson,
            long timestamp,
            long sseEventId,
            @Nullable String streamKey)
            implements SessionEvent {}

    /** A cancellation request. */
    record CancelEvent(String sessionId, RequestId requestId, long timestamp) implements SessionEvent {}

    /** A notification sent to or received from the client. */
    record NotificationEvent(
            String sessionId,
            String method,
            String paramsJson,
            long timestamp,
            long sseEventId,
            @Nullable String streamKey)
            implements SessionEvent {}
}
