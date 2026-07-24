/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.server.domain.ProgressToken;
import org.jspecify.annotations.Nullable;

/**
 * Handler-facing notification surface used from within an interaction (handler dispatch) context.
 * Extends the public {@link Notifications} logging surface with progress and keep-alive operations.
 */
public interface ContextNotifications extends Notifications {

    /**
     * Sends a progress notification.
     *
     * <p>The first server→client message on a POST also upgrades its response to an SSE stream and
     * arms the heartbeat, keeping the connection alive past {@code readerIdleTimeout} — so emitting
     * an early {@code progress(...)} is the keep-alive mechanism for long-running tools.
     *
     * <p>{@code progressToken} should be the client's request {@code _meta.progressToken}
     * (e.g. {@code ToolRequest.progressToken()}). When it is {@code null} the client did not opt
     * into progress, so the notification is silently dropped per the MCP spec — handlers may emit
     * progress unconditionally without null-checking the token.
     */
    void progress(@Nullable ProgressToken progressToken, double progress, double total, String message);

    /**
     * Sends a raw SSE comment line ({@code : message}) on the response stream, upgrading a buffered
     * POST response to {@code text/event-stream} if it has not started yet.
     *
     * <p>Unlike {@link #progress}, this needs no progress token — it carries no MCP message, only
     * transport-level bytes the client ignores. Use it to keep a long-running request's connection
     * alive when no progress token is available. A {@code null} or blank message emits a bare
     * {@code :} heartbeat comment. No-op when no outbound stream is bound to the context.
     */
    @ExperimentalApi
    void comment(@Nullable String message);

    /**
     * Sends empty SSE comment line ({@code :\r\n}).
     *
     * @see #comment(String)
     */
    @ExperimentalApi
    default void comment() {
        comment(null);
    }
}
