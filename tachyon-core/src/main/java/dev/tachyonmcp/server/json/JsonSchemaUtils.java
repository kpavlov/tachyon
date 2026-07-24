/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.annotations.InternalApi;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@InternalApi
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
