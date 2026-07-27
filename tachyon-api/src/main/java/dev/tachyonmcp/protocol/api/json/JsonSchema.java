/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.json;

import dev.tachyonmcp.protocol.api.json.spi.JsonSchemaFactory;

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

    /**
     * Creates a schema from encoded JSON, validating it via the {@link
     * JsonSchemaFactory} discovered through {@link
     * java.util.ServiceLoader} for {@link String}.
     *
     * @param json the JSON string
     * @return the parsed schema
     * @throws IllegalArgumentException if {@code json} is not valid JSON
     * @throws IllegalStateException if no {@code JsonSchemaFactory<String>} is registered
     */
    static JsonSchema parse(String json) {
        return from(json, String.class);
    }

    /**
     * Creates a schema from an already-parsed representation, via the {@link
     * JsonSchemaFactory} discovered through {@link
     * java.util.ServiceLoader} for {@code type}.
     *
     * @param source the source representation, e.g. a Jackson {@code JsonNode}
     * @param type   the source representation's type
     * @return the parsed schema
     * @throws IllegalStateException if no {@code JsonSchemaFactory<T>} is registered for {@code type}
     */
    static <T> JsonSchema from(T source, Class<T> type) {
        return JsonSchemas.from(source, type);
    }
}
