/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.server.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.server.json.spi.JsonSchemaFactory;
import tools.jackson.databind.JsonNode;

/**
 * Jackson {@link JsonNode}-backed {@link JsonDocumentFactory} and {@link JsonSchemaFactory}: wraps
 * an already-parsed tree without re-serializing it, retaining the node for {@link
 * JsonDocument#unwrap(Class)} instead of round-tripping through a JSON string.
 *
 * @author Konstantin Pavlov
 */
public final class JacksonNodeJsonFactory implements JsonDocumentFactory<JsonNode>, JsonSchemaFactory<JsonNode> {

    public static final JacksonNodeJsonFactory INSTANCE = new JacksonNodeJsonFactory();

    public JacksonNodeJsonFactory() {}

    /**
     * Provider factory used by {@link java.util.ServiceLoader} to obtain the singleton.
     */
    public static JacksonNodeJsonFactory provider() {
        return INSTANCE;
    }

    @Override
    public Class<JsonNode> sourceType() {
        return JsonNode.class;
    }

    @Override
    public JsonDocument toJsonDocument(JsonNode node) {
        return new JacksonNodeJsonDocument(node);
    }

    @Override
    public JsonSchema toJsonSchema(JsonNode node) {
        return new JacksonNodeJsonSchema(node);
    }
}
