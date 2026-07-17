/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.server.domain.LoggingLevel;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Base class holding the shared logging logic for {@link InternalNotifications} implementations.
 *
 * <p>It owns the single wire-shape builder ({@link #logParams}) and the gated {@code log} template:
 * every log funnels through {@link #shouldEmit} before {@link #send}, so gating cannot be bypassed.
 * Subclasses supply the transport ({@code send}/{@code progress}/{@code comment}) and, when they are
 * connection-bound, override {@link #shouldEmit} to apply capability and threshold policy.
 */
public abstract class AbstractNotifications implements InternalNotifications {

    /** The MCP method for structured log notifications. */
    public static final String LOG_METHOD = "notifications/message";

    /**
     * Builds the {@code notifications/message} params in wire order. Uses a {@link LinkedHashMap} so
     * a {@code null} {@code data} value is retained (spec-legal) rather than rejected by
     * {@code Map.of}, and an absent {@code logger} is omitted entirely.
     */
    public static Map<String, Object> logParams(LoggingLevel level, @Nullable String logger, @Nullable Object data) {
        var params = new LinkedHashMap<String, Object>(3);
        params.put("level", level.getValue());
        if (logger != null) {
            params.put("logger", logger);
        }
        params.put("data", data);
        return params;
    }

    /**
     * Decides whether a log message at the given level should be emitted. The single gate every
     * {@link #log} call funnels through. Defaults to always emitting; connection-bound
     * implementations override this to apply the client's configured threshold and the server's
     * advertised {@code logging} capability.
     */
    public boolean shouldEmit(LoggingLevel level) {
        return true;
    }

    @Override
    public void log(LoggingLevel level, @Nullable String logger, @Nullable Object data) {
        if (!shouldEmit(level)) {
            return;
        }
        send(LOG_METHOD, logParams(level, logger, data));
    }
}
