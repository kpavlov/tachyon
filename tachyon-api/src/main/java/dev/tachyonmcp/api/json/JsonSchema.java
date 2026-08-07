/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;

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
     * Creates a schema from encoded JSON, validating it via the {@link JsonSchemaFactory}
     * chain discovered through {@link java.util.ServiceLoader}.
     *
     * @param json the JSON string
     * @return the parsed schema
     * @throws IllegalArgumentException if {@code json} is not valid JSON
     * @throws IllegalStateException if no {@code JsonSchemaFactory} handles {@code String} sources
     */
    static JsonSchema parse(String json) {
        return from(json, String.class);
    }

    /**
     * Creates a schema from an already-parsed representation, via the {@link
     * JsonSchemaFactory} chain discovered through {@link java.util.ServiceLoader}: the
     * registered factory whose {@link JsonSchemaFactory#toJsonSchema(Object, Class)} covers
     * {@code type} wins.
     *
     * @param source the source representation, e.g. a Jackson {@code JsonNode}
     * @param type   the source representation's type
     * @return the parsed schema
     * @throws IllegalStateException if no {@code JsonSchemaFactory} is registered for {@code type}
     */
    @ExperimentalApi
    static <T> JsonSchema from(T source, Class<T> type) {
        return JsonSchemas.from(source, type);
    }

    /**
     * Resolves a schema for {@code type} through the {@link JsonSchemaFactory} resolution chain.
     * Registered factories are tried in ascending {@link JsonSchemaFactory#priority()} order and
     * the first non-empty result wins. The reference implementations ship with:
     *
     * <ul>
     *   <li>{@code KtSchemaResourceFactory} (tachyon-core) — loads a schema generated at build
     *       time, e.g. by the <a href="https://github.com/kpavlov/kt-schema">kt-schema</a>
     *       annotation processor, from {@code META-INF/kt-schema/schemas/<binary-class-name>.json}
     *       on {@code type}'s classloader.</li>
     *   <li>{@code KtSchemaReflectionFactory} (tachyon-kotlin-kt-schema) — generates the schema at
     *       runtime from {@code type}'s structure via reflection.</li>
     * </ul>
     *
     * <p>A build-time generated resource is therefore preferred automatically, and the runtime
     * reflection generator backstops types without one. The same chain also covers {@link
     * #from(Object, Class)}; both {@link #parse(String)} and this method resolve through it.
     *
     * @param type the type whose schema to resolve
     * @return the generated schema
     * @throws IllegalStateException if no registered {@link JsonSchemaFactory} produces a schema
     *     for {@code type}
     */
    @ExperimentalApi
    static JsonSchema generated(Class<?> type) {
        return JsonSchemas.generated(type);
    }
}
