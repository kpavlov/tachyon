/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Jackson {@link JsonNode}-backed {@link JsonDocumentFactory} and {@link JsonSchemaFactory}: wraps
 * an already-parsed tree without re-serializing it, retaining the node for
 * {@link JsonDocument#unwrap(Class)} instead of round-tripping through a JSON string.
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
    public Optional<JsonSchema> toJsonSchema(JsonNode source) {
        return Optional.of(new JacksonNodeJsonSchema(source));
    }
}
