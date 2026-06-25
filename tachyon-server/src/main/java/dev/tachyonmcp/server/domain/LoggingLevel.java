/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

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

    public String getValue() {
        return value;
    }

    public static LoggingLevel fromValue(String value) {
        for (var v : values()) {
            if (v.value.equals(value)) return v;
        }
        throw new IllegalArgumentException("Unexpected value: " + value);
    }
}
