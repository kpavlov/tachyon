/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.server.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.server.json.spi.JsonSchemaFactory;

/**
 * Tachyon's provider-neutral {@link JsonObject}-backed {@link JsonDocumentFactory} and {@link
 * JsonSchemaFactory}: wraps an already-built {@link JsonObject} without re-serializing it,
 * retaining it for {@link JsonDocument#unwrap(Class)} instead of round-tripping through a JSON
 * string.
 *
 * @author Konstantin Pavlov
 */
public final class JacksonObjectJsonFactory implements JsonDocumentFactory<JsonObject>, JsonSchemaFactory<JsonObject> {

    public static final JacksonObjectJsonFactory INSTANCE = new JacksonObjectJsonFactory();

    public JacksonObjectJsonFactory() {}

    /**
     * Provider factory used by {@link java.util.ServiceLoader} to obtain the singleton.
     */
    public static JacksonObjectJsonFactory provider() {
        return INSTANCE;
    }

    @Override
    public Class<JsonObject> sourceType() {
        return JsonObject.class;
    }

    @Override
    public JsonDocument toJsonDocument(JsonObject object) {
        return new JacksonObjectJsonDocument(object);
    }

    @Override
    public JsonSchema toJsonSchema(JsonObject object) {
        return new JacksonObjectJsonSchema(object);
    }
}
