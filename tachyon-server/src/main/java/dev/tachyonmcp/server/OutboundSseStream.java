/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.SseEvent;
import org.jspecify.annotations.Nullable;

/**
 * Outbound SSE stream bound to the current dispatch context. Allows a handler executing a
 * JSON-RPC request to lazily upgrade the response transport to a Server-Sent Events stream so
 * the server can push notifications and requests to the client before the final response.
 *
 * <p>The stream is dormant until {@link #start()} is called (typically on the first server-to-client
 * message issued during dispatch). Once started, subsequent events flow as SSE frames. The final
 * response is appended as an SSE event by the dispatcher after the handler completes, then the
 * stream is closed.
 *
 * <p>Implementations are transport-specific. The Netty implementation ({@code PostSseStream}) ties
 * this to the HTTP POST response channel; other transports may bind it differently.
 */
public interface OutboundSseStream {

    /**
     * A stable key identifying this stream within its session, used to tag event-log entries and
     * to suffix SSE event ids ({@code <n>#<key>}) so a {@code Last-Event-ID} can be correlated
     * back to the originating stream for per-stream replay. {@code null} identifies the session's
     * general-purpose GET stream.
     */
    default @Nullable String streamKey() {
        return null;
    }

    /**
     * Activates the SSE stream and emits initial framing (headers, retry directive).
     * Idempotent — subsequent calls are no-ops. May be called from any thread.
     */
    void start();

    /**
     * @return {@code true} once {@link #start()} has completed.
     */
    boolean started();

    /**
     * Writes an SSE event. If {@link #start()} has not been called yet, the event is buffered
     * and emitted when the stream upgrades. May be called from any thread.
     *
     * @param event the SSE event to write
     */
    void writeEvent(@Nullable SseEvent event);

    /**
     * Closes the SSE stream. Idempotent — subsequent calls are no-ops.
     */
    void close();
}
