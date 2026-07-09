/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Validates JSON data against a JSON Schema.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface JsonSchemaValidator {

    /**
     * The no-op validator singleton that accepts all input. Passing this instance disables
     * validation entirely, including the parsing work needed to prepare data for validation.
     */
    JsonSchemaValidator NOOP = (schema, arguments) -> List.of();

    /** Validates the given arguments against the schema and returns any errors. */
    List<SchemaValidationError> validate(JsonNode schema, JsonNode arguments);

    /** Returns {@link #NOOP}, the no-op validator that accepts all input. */
    static JsonSchemaValidator noop() {
        return NOOP;
    }
}
