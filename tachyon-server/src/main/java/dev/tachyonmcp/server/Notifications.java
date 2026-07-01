/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import org.jspecify.annotations.Nullable;

/** Sends MCP notifications from within a handler dispatch context. */
public interface Notifications {

    /** Sends a generic notification with the given method and params. */
    void send(String method, Object params);

    /** Sends a progress notification. */
    void progress(@Nullable Object progressToken, double progress, double total, String message);

    /** Sends an info-level log message. */
    void info(String logger, Object data);

    /** Sends a warning-level log message. */
    void warning(String logger, Object data);

    /** Sends an error-level log message. */
    void error(String logger, Object data);
}
