/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface JsonSchemaValidator {

    void validate(JsonNode schema, JsonNode arguments) throws RuntimeException;

    static JsonSchemaValidator noop() {
        return (schema, arguments) -> {};
    }
}
