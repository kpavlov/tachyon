/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.annotations.InternalApi;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@InternalApi
public class JsonSchemaUtils {
    private JsonSchemaUtils() {
        // noop
    }

    /**
     * Parses a JSON schema string, returning {@code null} for null input.
     */
    public static @Nullable JsonSchema parseSchema(@Nullable String json) {
        if (json == null) return null;
        try {
            JsonUtils.parse(json);
            return JsonSchema.of(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Schema is not valid JSON: " + json, e);
        }
    }

    /**
     * Validates args against schema; returns a joined error message, or null when valid / no schema.
     */
    public static @Nullable String validateArguments(
            JsonSchemaValidator validator, @Nullable JsonSchema schema, @Nullable Map<String, JsonNode> args) {
        if (schema == null) return null;
        var document = JsonDocument.of(JsonUtils.writeString(args == null ? Map.of() : args));
        var errors = validator.validate(schema, document);
        return errors.isEmpty() ? null : SchemaValidationError.join(errors);
    }
}
