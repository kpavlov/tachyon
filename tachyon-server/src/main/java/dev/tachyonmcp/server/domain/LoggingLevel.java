/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

/** Logging levels matching the MCP protocol specification. Ordered by severity ascending. */
public enum LoggingLevel {
    DEBUG("debug"),
    INFO("info"),
    NOTICE("notice"),
    WARNING("warning"),
    ERROR("error"),
    CRITICAL("critical"),
    ALERT("alert"),
    EMERGENCY("emergency");

    private final String value;

    LoggingLevel(String value) {
        this.value = value;
    }

    /** Returns the wire-level string for this logging level. */
    public String getValue() {
        return value;
    }

    /** Parses a logging level from its wire-level string. */
    public static LoggingLevel fromValue(String value) {
        for (var v : values()) {
            if (v.value.equals(value)) return v;
        }
        throw new IllegalArgumentException("Unexpected value: " + value);
    }
}
