/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import java.util.List;
import tools.jackson.databind.JsonNode;

/** Validates JSON data against a JSON Schema. */
@FunctionalInterface
public interface JsonSchemaValidator {

    /** Validates the given arguments against the schema and returns any errors. */
    List<SchemaValidationError> validate(JsonNode schema, JsonNode arguments);

    /** Returns a no-op validator that accepts all input. */
    static JsonSchemaValidator noop() {
        return (schema, arguments) -> List.of();
    }
}
