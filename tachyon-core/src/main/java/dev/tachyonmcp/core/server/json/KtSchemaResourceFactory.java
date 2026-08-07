/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * {@link JsonSchemaFactory} that loads a schema generated at build time by the
 * <a href="https://github.com/kpavlov/kt-schema">kt-schema</a> annotation processor from
 * {@code META-INF/kt-schema/schemas/<binary-class-name>.json} on the target class's classloader.
 *
 * <p>Returns {@link Optional#empty()} when no such resource exists for a type, letting the
 * resolution chain continue to a lower-priority factory (e.g. the runtime reflection generator).
 * Ships in {@code tachyon-core} so Java-only servers resolve codegen schemas without adding a
 * Kotlin integration artifact.
 *
 * @author Konstantin Pavlov
 */
@ExperimentalApi
public final class KtSchemaResourceFactory implements JsonSchemaFactory {

    public KtSchemaResourceFactory() {}

    @Override
    public Optional<JsonSchema> tryGenerate(Class<?> type) {
        var path = "META-INF/kt-schema/schemas/%s.json".formatted(type.getName().replace('.', '/'));
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(JsonSchema.of(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read schema resource: " + path, e);
        }
    }
}
