/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared JSON parsing and writing for the server payload boundary. Single mapper instance for
 * schema parsing, argument encoding and structured-value handling.
 *
 * @author Konstantin Pavlov
 */
public final class JsonUtils {

    private static final JsonMapper MAPPER = new JsonMapper();

    private JsonUtils() {}

    static JsonMapper mapper() {
        return MAPPER;
    }

    /** Parses a JSON string into a tree. */
    public static JsonNode parse(String json) {
        return MAPPER.readTree(json);
    }

    /** Writes a value as a JSON string. */
    public static String writeString(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
