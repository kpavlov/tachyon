/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskSupport {
    FORBIDDEN("forbidden"),
    OPTIONAL("optional"),
    REQUIRED("required");

    private final String value;

    TaskSupport(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
