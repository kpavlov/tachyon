/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseEvent;

/**
 * Routes server-to-client SSE events to the appropriate transport channel.
 *
 * <p>When a request handler is mid-dispatch, an active {@link OutboundSseStream} can be bound
 * in the dispatch context; the router opportunistically diverts events for the dispatched session
 * to that stream (lazy SSE upgrade of the transport response). Events for any other session
 * continue through the standard GET-SSE pipeline (hot buffer + flush).
 *
 * <p>This abstraction keeps {@link Server#sendRequest} / {@link Server#sendNotification}
 * free of transport-aware branching.
 */
public interface MessageRouter {

    /**
     * Attempts to route the given event via a bound {@link OutboundSseStream}.
     *
     * @param session the target session for the event
     * @param event   the SSE event to deliver
     * @return {@code true} if the event was written to an {@link OutboundSseStream} (caller
     *         should NOT fall through to GET-SSE); {@code false} otherwise (caller should buffer
     *         + flush via the session's connection)
     */
    boolean tryRoute(Session session, SseEvent event);
}
