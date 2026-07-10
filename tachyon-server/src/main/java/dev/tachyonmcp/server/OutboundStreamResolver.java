/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.runtime.Session;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the transport channel server-to-client SSE events should be delivered on.
 *
 * <p>When a request handler is mid-dispatch, an active {@link OutboundSseStream} can be bound
 * in the dispatch context; the resolver opportunistically diverts events for the dispatched
 * session to that stream (lazy SSE upgrade of the transport response). Events for any other
 * session continue through the standard GET-SSE pipeline (hot buffer + flush).
 *
 * <p>This abstraction keeps {@link TachyonServer} / {@link McpDispatcher}
 * free of transport-aware branching.
 */
@InternalApi
public interface OutboundStreamResolver {

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
