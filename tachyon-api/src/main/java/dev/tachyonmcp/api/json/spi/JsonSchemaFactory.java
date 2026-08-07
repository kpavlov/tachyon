/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json.spi;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonSchema;
import java.util.Optional;

/**
 * Pluggable source of {@link JsonSchema} documents.
 *
 * <p>Factories cover two resolution paths on {@link JsonSchema}:
 * <ul>
 *   <li><b>from a source representation</b> — {@code JsonSchema.from(source, type)} parses
 *       {@code source} (e.g. a JSON string or an already-parsed node) via
 *       {@link #toJsonSchema(Object, Class)};</li>
 *   <li><b>from a type alone</b> — {@code JsonSchema.generated(type)} derives the schema without
 *       any instance via {@link #tryGenerate(Class)} (e.g. from a build-time codegen resource or
 *       runtime reflection).</li>
 * </ul>
 *
 * <p>Factories form a single resolution chain: both entry points try the registered factories in
 * ascending {@link #priority()} order and use the first non-empty result. An implementation
 * returns {@link Optional#empty()} on the paths it does not cover so resolution continues with the
 * next factory. Fabricating an empty result for a particular type/source is what lets a
 * low-priority resource factory and a high-priority reflection generator coexist.
 *
 * <p>Discoverable via {@link java.util.ServiceLoader}: implementations register themselves in
 * {@code META-INF/services/dev.tachyonmcp.api.json.spi.JsonSchemaFactory}.
 *
 * @author Konstantin Pavlov
 */
@ExperimentalApi
public interface JsonSchemaFactory {

    /**
     * Returns the position of this factory in the resolution chain; factories are tried in
     * ascending order of priority. Lower numbers are attempted first. Defaults to {@code 0}.
     *
     * @return the chain priority
     */
    default int priority() {
        return 0;
    }

    /**
     * Parses a schema out of an instance of a source representation, or {@link Optional#empty()}
     * if the source type {@code type} is not one this factory covers.
     *
     * @param source the source representation, e.g. a JSON string or a parsed node
     * @param type   the source representation type
     * @return the parsed schema, or empty if this factory does not handle {@code type}
     * @throws IllegalArgumentException if this factory handles {@code type} but {@code source} is
     *     not valid JSON
     */
    default Optional<JsonSchema> toJsonSchema(Object source, Class<?> type) {
        return Optional.empty();
    }

    /**
     * Generates a schema for {@code type} without a source instance, or {@link
     * Optional#empty()} if this factory cannot produce one for the given type.
     *
     * @param type the type whose schema to generate
     * @return the generated schema, or empty if this factory does not cover {@code type}
     */
    default Optional<JsonSchema> tryGenerate(Class<?> type) {
        return Optional.empty();
    }
}
