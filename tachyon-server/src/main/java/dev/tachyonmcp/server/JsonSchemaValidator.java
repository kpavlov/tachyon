/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import java.util.List;
import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface JsonSchemaValidator {

    List<SchemaValidationError> validate(JsonNode schema, JsonNode arguments);

    static JsonSchemaValidator noop() {
        return (schema, arguments) -> List.of();
    }
}
