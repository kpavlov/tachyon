/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Optional;

/**
 * Test-only {@link JsonSchemaFactory} with a higher chain priority ({@code 10}), mirroring a
 * runtime generator that backstops types no build-time resource covers.
 */
public final class ChainPriorityHighFactory implements JsonSchemaFactory {

    public static final class HighTarget {}

    public ChainPriorityHighFactory() {}

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Optional<JsonSchema> tryGenerate(Class<?> type) {
        if (type == HighTarget.class) {
            return Optional.of(JsonSchema.of("{\"title\":\"high\"}"));
        }
        return Optional.empty();
    }
}
