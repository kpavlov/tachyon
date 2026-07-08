/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */
package dev.tachyonmcp.server.json;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public class JsonSchemaUtils {
    private JsonSchemaUtils() {
        // noop
    }

    /**
     * Parses a JSON schema string, returning {@code null} for null input.
     */
    public static @Nullable JsonNode parseSchema(@Nullable String json) {
        if (json == null) return null;
        try {
            return JsonUtils.parse(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Schema is not valid JSON: " + json, e);
        }
    }

    /**
     * Parses a JSON schema string, failing fast with the tool name on invalid JSON.
     */
    public static @Nullable JsonNode parseSchema(@Nullable String json, String toolName) {
        if (json == null) return null;
        try {
            return JsonUtils.parse(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Tool '" + toolName + "' schema is not valid JSON: " + json, e);
        }
    }
}
