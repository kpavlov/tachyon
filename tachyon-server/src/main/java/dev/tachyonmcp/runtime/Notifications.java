/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import org.jspecify.annotations.Nullable;

/**
 * Sends notifications from within an interaction (handler dispatch) context.
 */
public interface Notifications {

    /**
     * No-op sender for synthetic contexts that are not bound to a live connection.
     */
    Notifications NOOP = new Notifications() {
        @Override
        public void send(String method, Object params) {}

        @Override
        public void progress(@Nullable Object progressToken, double progress, double total, String message) {}

        @Override
        public void comment(@Nullable String message) {}

        @Override
        public void info(String logger, Object data) {}

        @Override
        public void warning(String logger, Object data) {}

        @Override
        public void error(String logger, Object data) {}
    };

    /**
     * Sends a generic notification with the given method and params.
     */
    void send(String method, Object params);

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
    void progress(@Nullable Object progressToken, double progress, double total, String message);

    /**
     * Sends a raw SSE comment line ({@code : message}) on the response stream, upgrading a buffered
     * POST response to {@code text/event-stream} if it has not started yet.
     *
     * <p>Unlike {@link #progress}, this needs no progress token — it carries no MCP message, only
     * transport-level bytes the client ignores. Use it to keep a long-running request's connection
     * alive when no progress token is available. A {@code null} or blank message emits a bare
     * {@code :} heartbeat comment. No-op when no outbound stream is bound to the context.
     */
    void comment(@Nullable String message);

    /**
     * Sends empty SSE comment line ({@code :\r\n}).
     *
     * @see #comment(String)
     */
    default void comment() {
        comment(null);
    }

    /**
     * Sends an info-level log message.
     */
    void info(String logger, Object data);

    /**
     * Sends a warning-level log message.
     */
    void warning(String logger, Object data);

    /**
     * Sends an error-level log message.
     */
    void error(String logger, Object data);
}
