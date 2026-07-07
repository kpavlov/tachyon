/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.Session;
import org.jspecify.annotations.Nullable;

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
     * Resolves the {@link OutboundSseStream} bound in the current dispatch context for the given
     * session, if any. The caller needs the stream <em>before</em> building the event (its
     * {@link OutboundSseStream#streamKey()} goes into the SSE event id and the event log), so
     * resolution and delivery are separate steps.
     *
     * @param session the target session for the event
     * @return the bound stream to deliver on, or {@code null} to fall through to the session's
     *         GET-SSE connection
     */
    @Nullable
    OutboundSseStream resolve(Session session);
}
