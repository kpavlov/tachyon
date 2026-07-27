/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.json.JsonDocument;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Shared {@link JsonDocument#json()}/{@link JsonDocument#unwrap(Class)} for records wrapping a
 * retained Jackson {@link JsonNode} (or a subtype, e.g. {@code ObjectNode}) instead of
 * round-tripping through a JSON string.
 */
interface JacksonNodeBacked extends JsonDocument {

    JsonNode node();

    @Override
    default String json() {
        return node().toString();
    }

    @Override
    default <T> Optional<T> unwrap(Class<T> type) {
        return type.isInstance(node()) ? Optional.of(type.cast(node())) : Optional.empty();
    }
}
