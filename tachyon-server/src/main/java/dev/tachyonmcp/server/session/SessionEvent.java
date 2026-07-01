/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

/** A recorded session event — request, response, notification, or cancellation. */
public sealed interface SessionEvent {

    /** The session this event belongs to. */
    String sessionId();

    /** Timestamp of the event (epoch millis). */
    long timestamp();

    /** SSE event ID (for replay), or -1 if not assigned. */
    default long sseEventId() {
        return -1;
    }

    /** An inbound request from the client. */
    record RequestEvent(String sessionId, Object requestId, String method, String paramsJson, long timestamp)
            implements SessionEvent {}

    /** An outbound (server-to-client) request. */
    record OutboundRequestEvent(
            String sessionId, Object requestId, String method, String paramsJson, long timestamp, long sseEventId)
            implements SessionEvent {}

    /** A response sent to the client. */
    record ResponseEvent(String sessionId, Object requestId, String resultJson, long timestamp, long sseEventId)
            implements SessionEvent {

        public ResponseEvent(String sessionId, Object requestId, String resultJson, long timestamp) {
            this(sessionId, requestId, resultJson, timestamp, -1);
        }
    }

    /** A cancellation request. */
    record CancelEvent(String sessionId, Object requestId, long timestamp) implements SessionEvent {}

    /** A notification sent to or received from the client. */
    record NotificationEvent(String sessionId, String method, String paramsJson, long timestamp, long sseEventId)
            implements SessionEvent {

        public NotificationEvent(String sessionId, String method, String paramsJson, long timestamp) {
            this(sessionId, method, paramsJson, timestamp, -1);
        }
    }
}
