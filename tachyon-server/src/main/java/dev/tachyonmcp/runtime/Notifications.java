/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.server.domain.LoggingLevel;
import org.jspecify.annotations.Nullable;

/**
 * Emits structured MCP {@code notifications/message} logs.
 *
 * <p>This is the public logging surface. The single abstract method
 * {@link #log(LoggingLevel, String, Object)} carries all behavior; the other methods are pure
 * delegating conveniences, so implementations (including lambdas) only implement that one method.
 * Threshold and capability gating, plus the wire-shape construction, live in
 * {@link AbstractNotifications} — never in this interface.
 */
@FunctionalInterface
public interface Notifications {

    /**
     * Emits a structured MCP log message.
     *
     * @param level the message severity
     * @param logger the optional logger name
     * @param data JSON-serializable message data, including {@code null}
     */
    void log(LoggingLevel level, @Nullable String logger, @Nullable Object data);

    /** Emits a structured MCP log message without a logger name. */
    default void log(LoggingLevel level, @Nullable Object data) {
        log(level, null, data);
    }

    /** Emits an info-level log message. */
    default void info(String logger, @Nullable Object data) {
        log(LoggingLevel.INFO, logger, data);
    }

    /** Emits a warning-level log message. */
    default void warning(String logger, @Nullable Object data) {
        log(LoggingLevel.WARNING, logger, data);
    }

    /** Emits an error-level log message. */
    default void error(String logger, @Nullable Object data) {
        log(LoggingLevel.ERROR, logger, data);
    }
}
