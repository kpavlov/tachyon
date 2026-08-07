/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.JsonSchemaValidator;
import dev.tachyonmcp.api.json.SchemaValidationError;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@InternalApi
public class JsonSchemaUtils {
    private JsonSchemaUtils() {
        // noop
    }

    /**
     * Validates that a tool schema is well-formed JSON with an object root declaring
     * {@code "type": "object"}.
     *
     * @param factory    parses and validates the schema's raw JSON; must handle {@link String}
     *     sources, otherwise schemas cannot be validated
     * @param schemaKind the kind of schema being validated
     * @param toolName   the name of the tool owning the schema
     * @param schema     the schema to validate, or {@code null}
     * @throws IllegalArgumentException if the schema is not valid JSON, or its root is invalid
     */
    public static void validateSchemaRoot(
            JsonSchemaFactory<?> factory, String schemaKind, String toolName, @Nullable JsonSchema schema) {
        if (schema == null) return;
        if (factory.sourceType() != String.class) {
            throw new IllegalStateException(
                    "Configured schema factory '" + factory.getClass().getName()
                            + "' does not handle String sources and cannot validate the schema of tool '"
                            + toolName + "'.");
        }
        @SuppressWarnings("unchecked")
        var validated = ((JsonSchemaFactory<String>) factory).toJsonSchema(schema.json());
        if (validated.isEmpty()) {
            throw new IllegalStateException(
                    "Configured schema factory does not handle String sources and cannot validate the schema of tool '"
                            + toolName + "'.");
        }
        var node = JsonUtils.parse(validated.get());
        final String detail;
        if (!node.isObject()) {
            detail = "got: " + node.getNodeType();
        } else if (!node.has("type")) {
            detail = "missing \"type\"";
        } else if (!"object".equals(node.get("type").asString())) {
            detail = "got: " + node.get("type");
        } else {
            return;
        }
        throw new IllegalArgumentException(
                "Tool '" + toolName + "' " + schemaKind + " root must declare \"type\": \"object\", " + detail);
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
