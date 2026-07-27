/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.server.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.server.json.spi.JsonSchemaFactory;
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

    /**
     * Provider factory used by {@link java.util.ServiceLoader} to obtain the singleton.
     */
    public static JacksonObjectJsonFactory provider() {
        return INSTANCE;
    }

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
