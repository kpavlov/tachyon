/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Optional;

/**
 * Test-only {@link JsonSchemaFactory} at the default chain priority ({@code 0}). Supplies a
 * schema for {@link LowTarget}, mirroring a build-time resource factory: it claims the default
 * slot so a higher-priority generator underneath never gets consulted for that type.
 */
public final class ChainPriorityLowFactory implements JsonSchemaFactory<Class<?>> {

    public static final class LowTarget {}

    public ChainPriorityLowFactory() {}

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<Class<?>> sourceType() {
        return (Class) Class.class;
    }

    @Override
    public Optional<JsonSchema> toJsonSchema(Class<?> type) {
        if (type == LowTarget.class) {
            return Optional.of(JsonSchema.of("{\"title\":\"low\"}"));
        }
        return Optional.empty();
    }
}
