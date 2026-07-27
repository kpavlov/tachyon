/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.json.JsonDocument;
import dev.tachyonmcp.json.JsonSchema;
import dev.tachyonmcp.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.json.spi.JsonSchemaFactory;
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
