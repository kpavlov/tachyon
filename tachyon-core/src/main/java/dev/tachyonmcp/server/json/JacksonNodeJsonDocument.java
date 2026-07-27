/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import java.util.Optional;
import tools.jackson.databind.JsonNode;

record JacksonNodeJsonDocument(JsonNode node) implements JsonDocument {

    @Override
    public String json() {
        return node.toString();
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return type.isInstance(node) ? Optional.of(type.cast(node)) : Optional.empty();
    }
}
