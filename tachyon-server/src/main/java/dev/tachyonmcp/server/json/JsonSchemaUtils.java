/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */
package dev.tachyonmcp.server.json;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

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

    /**
     * Validates args against schema; returns a joined error message, or null when valid / no schema.
     */
    public static @Nullable String validateArguments(
            JsonSchemaValidator validator, @Nullable JsonNode schema, @Nullable Map<String, JsonNode> args) {
        if (schema == null) return null;
        var node = JsonNodeFactory.instance.objectNode();
        if (args != null) node.setAll(args);
        var errors = validator.validate(schema, node);
        return errors.isEmpty() ? null : SchemaValidationError.join(errors);
    }
}
