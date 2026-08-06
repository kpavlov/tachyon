/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * An immutable, encoded JSON Schema.
 *
 * <p>Both object and boolean schemas are supported. The server validates the schema against its
 * declared dialect, or JSON Schema 2020-12 when the dialect is absent.
 *
 * @author Konstantin Pavlov
 */
public interface JsonSchema extends JsonDocument {

    /** Returns a schema that accepts any JSON object. */
    static JsonSchema objectSchema() {
        return DefaultJsonSchema.OBJECT;
    }

    /**
     * Creates a schema from encoded JSON without parsing and verifying if the schema is correct.
     *
     * @throws IllegalArgumentException when json is null or blank string
     */
    static JsonSchema of(String json) {
        return new DefaultJsonSchema(JsonDocuments.requireContent(json));
    }

    /**
     * Creates a schema from encoded JSON, validating it via the {@link
     * JsonSchemaFactory} discovered through {@link
     * java.util.ServiceLoader} for {@link String}.
     *
     * @param json the JSON string
     * @return the parsed schema
     * @throws IllegalArgumentException if {@code json} is not valid JSON
     * @throws IllegalStateException if no {@code JsonSchemaFactory<String>} is registered
     */
    static JsonSchema parse(String json) {
        return from(json, String.class);
    }

    /**
     * Creates a schema from an already-parsed representation, via the {@link
     * JsonSchemaFactory} discovered through {@link
     * java.util.ServiceLoader} for {@code type}.
     *
     * @param source the source representation, e.g. a Jackson {@code JsonNode}
     * @param type   the source representation's type
     * @return the parsed schema
     * @throws IllegalStateException if no {@code JsonSchemaFactory<T>} is registered for {@code type}
     */
    @ExperimentalApi
    static <T> JsonSchema from(T source, Class<T> type) {
        return JsonSchemas.from(source, type);
    }

    /**
     * Loads a schema generated at build time for {@code type}, e.g. by the
     * <a href="https://github.com/kpavlov/kt-schema">kt-schema</a> annotation processor, from
     * {@code META-INF/kt-schema/schemas/<binary-class-name>.json} on {@code type}'s classloader.
     *
     * @param type the annotated Java or Kotlin type whose schema was generated at build time
     * @return the loaded schema
     * @throws IllegalStateException if no generated schema resource is found for {@code type}
     */
    @ExperimentalApi
    static JsonSchema generated(Class<?> type) {
        var path = "META-INF/kt-schema/schemas/%s.json".formatted(type.getName().replace('.', '/'));
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing generated schema resource: " + path);
            }
            return of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read schema resource: " + path, e);
        }
    }
}
