/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

public sealed interface SessionEvent {

    String sessionId();

    long timestamp();

    default long sseEventId() {
        return -1;
    }

    record RequestEvent(String sessionId, Object requestId, String method, String paramsJson, long timestamp)
            implements SessionEvent {}

    record OutboundRequestEvent(
            String sessionId, Object requestId, String method, String paramsJson, long timestamp, long sseEventId)
            implements SessionEvent {}

    record ResponseEvent(String sessionId, Object requestId, String resultJson, long timestamp, long sseEventId)
            implements SessionEvent {

        public ResponseEvent(String sessionId, Object requestId, String resultJson, long timestamp) {
            this(sessionId, requestId, resultJson, timestamp, -1);
        }
    }

    record CancelEvent(String sessionId, Object requestId, long timestamp) implements SessionEvent {}

    record NotificationEvent(String sessionId, String method, String paramsJson, long timestamp, long sseEventId)
            implements SessionEvent {

        public NotificationEvent(String sessionId, String method, String paramsJson, long timestamp) {
            this(sessionId, method, paramsJson, timestamp, -1);
        }
    }
}
