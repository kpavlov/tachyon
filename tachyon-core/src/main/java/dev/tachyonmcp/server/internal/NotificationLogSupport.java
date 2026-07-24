/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.internal;

import dev.tachyonmcp.server.domain.LoggingLevel;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Shared {@code notifications/message} wire-shape helpers for the two independent notification
 * senders — {@code DefaultDispatchContext.NotificationsImpl} (per-request) and
 * {@code DefaultTachyonServer.broadcastLog} (server-wide) — which live in different packages and
 * gate emission with their own, unrelated {@code shouldEmit} logic.
 */
public final class NotificationLogSupport {

    /** The MCP method for structured log notifications. */
    public static final String LOG_METHOD = "notifications/message";

    private NotificationLogSupport() {}

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
}
