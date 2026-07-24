/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

/**
 * An immutable, encoded JSON Schema.
 *
 * <p>Both object and boolean schemas are supported. The server validates the schema against its
 * declared dialect, or JSON Schema 2020-12 when the dialect is absent.
 *
 * @author Konstantin Pavlov
 */
public interface JsonSchema extends JsonDocument {

    /** Returns a schema that accepts any JSON object. */
    static JsonSchema objectSchema() {
        return DefaultJsonSchema.OBJECT;
    }

    /** Creates a schema from encoded JSON. */
    static JsonSchema of(String json) {
        return new DefaultJsonSchema(JsonDocuments.requireContent(json));
    }
}
