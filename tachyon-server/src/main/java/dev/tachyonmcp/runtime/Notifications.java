/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.server.domain.LoggingLevel;
import java.util.LinkedHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Sends notifications from within an interaction (handler dispatch) context.
 */
public interface Notifications {

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

    /**
     * Decides whether a log message at the given level should be emitted. This is the single gate
     * every {@link #log} call funnels through, so gating cannot be bypassed by a caller.
     *
     * <p>The base implementation always returns {@code true} (an unbound sender writes
     * unconditionally). Connection-bound implementations override this to apply the client's
     * configured threshold and the server's advertised {@code logging} capability.
     *
     * @param level the message severity
     * @return {@code true} if a {@code notifications/message} at this level should be sent
     */
    default boolean shouldEmit(LoggingLevel level) {
        return true;
    }

    /**
     * Sends a structured MCP {@code notifications/message} log, gated by {@link #shouldEmit}.
     *
     * <p>This default is the unconditional wire writer once {@link #shouldEmit} allows the level:
     * it serializes the params and delegates to {@link #send}. All threshold and capability policy
     * lives in {@link #shouldEmit}, never here — so overriding that predicate is the only supported
     * way to change what gets emitted.
     *
     * @param level the message severity
     * @param logger the optional logger name
     * @param data JSON-serializable message data, including {@code null}
     */
    default void log(LoggingLevel level, @Nullable String logger, @Nullable Object data) {
        if (!shouldEmit(level)) {
            return;
        }
        var params = new LinkedHashMap<String, Object>(3);
        params.put("level", level.getValue());
        if (logger != null) {
            params.put("logger", logger);
        }
        params.put("data", data);
        send("notifications/message", params);
    }

    /**
     * Sends a structured MCP log message without a logger name.
     */
    default void log(LoggingLevel level, @Nullable Object data) {
        log(level, null, data);
    }

    /**
     * Sends an info-level log message.
     */
    default void info(String logger, @Nullable Object data) {
        log(LoggingLevel.INFO, logger, data);
    }

    /**
     * Sends a warning-level log message.
     */
    default void warning(String logger, @Nullable Object data) {
        log(LoggingLevel.WARNING, logger, data);
    }

    /**
     * Sends an error-level log message.
     */
    default void error(String logger, @Nullable Object data) {
        log(LoggingLevel.ERROR, logger, data);
    }
}
