/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.protocol.api.json.JsonDocument;
import dev.tachyonmcp.protocol.api.json.JsonObject;
import dev.tachyonmcp.protocol.api.json.JsonSchema;
import dev.tachyonmcp.protocol.api.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.protocol.api.json.spi.JsonSchemaFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Jackson {@link ObjectNode}-backed {@link JsonDocumentFactory} and {@link JsonSchemaFactory}:
 * wraps an already-parsed object node without re-serializing it, retaining it for {@link
 * JsonDocument#unwrap(Class)} instead of round-tripping through a JSON string. The returned {@link
 * JsonDocument} also implements {@link JsonObject}, exposing typed property navigation directly
 * over the wrapped node.
 *
 * @author Konstantin Pavlov
 */
public final class JacksonObjectJsonFactory implements JsonDocumentFactory<ObjectNode>, JsonSchemaFactory<ObjectNode> {

    public static final JacksonObjectJsonFactory INSTANCE = new JacksonObjectJsonFactory();

    public JacksonObjectJsonFactory() {}

    @Override
    public Class<ObjectNode> sourceType() {
        return ObjectNode.class;
    }

    @Override
    public JsonDocument toJsonDocument(ObjectNode node) {
        return new JacksonObjectJsonDocument(node);
    }

    @Override
    public JsonSchema toJsonSchema(ObjectNode node) {
        return new JacksonObjectJsonSchema(node);
    }
}
