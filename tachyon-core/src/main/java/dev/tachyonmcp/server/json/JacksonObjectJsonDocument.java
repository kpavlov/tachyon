/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import java.util.Optional;

record JacksonObjectJsonDocument(JsonObject object) implements JsonDocument {

    @Override
    public String json() {
        return object.json();
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return type.isInstance(object) ? Optional.of(type.cast(object)) : Optional.empty();
    }
}
