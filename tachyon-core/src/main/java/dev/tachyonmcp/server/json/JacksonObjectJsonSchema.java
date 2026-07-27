/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import java.util.Optional;

record JacksonObjectJsonSchema(JsonObject object) implements JsonSchema {

    @Override
    public String json() {
        return object.json();
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return type.isInstance(object) ? Optional.of(type.cast(object)) : Optional.empty();
    }
}
