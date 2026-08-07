/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Optional;
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
public final class JacksonObjectJsonFactory implements JsonDocumentFactory<ObjectNode>, JsonSchemaFactory {

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
    public Optional<JsonSchema> toJsonSchema(Object source, Class<?> type) {
        if (type != ObjectNode.class) {
            return Optional.empty();
        }
        return Optional.of(new JacksonObjectJsonSchema((ObjectNode) source));
    }

    /**
     * Wraps the given node as a schema without type probing. Equivalent to {@code
     * toJsonSchema(node, ObjectNode.class).orElseThrow()}.
     */
    public JsonSchema toJsonSchema(ObjectNode node) {
        return new JacksonObjectJsonSchema(node);
    }
}
