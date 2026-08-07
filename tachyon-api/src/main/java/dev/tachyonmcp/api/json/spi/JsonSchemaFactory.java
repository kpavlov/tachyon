/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json.spi;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonSchema;
import java.util.Optional;

/**
 * Pluggable source of {@link JsonSchema} documents, generic over the JSON source representation
 * {@code T} it accepts.
 *
 * <p>Factories cover two resolution paths on {@link JsonSchema} through a single {@link
 * #sourceType()} and {@link #toJsonSchema(Object)}:
 * <ul>
 *   <li><b>parsed sources</b> — {@code JsonSchema.from(source, type)} parses {@code source} (e.g.
 *       a JSON string or an already-parsed node) via the factory whose {@link #sourceType()}
 *       equals {@code type};</li>
 *   <li><b>type-only generation</b> — {@code JsonSchema.generated(type)} derives a schema without
 *       an instance. Generation factories expose {@link Class} as their source type and treat the
 *       target type as the source (e.g. a build-time codegen resource or runtime reflection).</li>
 * </ul>
 *
 * <p>Factories of the same source type form a resolution chain: entry points try the registered
 * factories in ascending {@link #priority()} order and use the first non-empty result. An
 * implementation returns {@link Optional#empty()} on sources it does not cover so resolution
 * continues with the next factory. Fabricating an empty result for a given target is what lets a
 * low-priority resource factory and a high-priority reflection generator coexist.
 *
 * <p>Discoverable via {@link java.util.ServiceLoader}: implementations register themselves in
 * {@code META-INF/services/dev.tachyonmcp.api.json.spi.JsonSchemaFactory}.
 *
 * @author Konstantin Pavlov
 */
@ExperimentalApi
public interface JsonSchemaFactory<T> {

    /**
     * Returns the type of the JSON source representation this factory accepts, e.g. {@link
     * String} or a JSON object type.
     *
     * @return the source representation type
     */
    Class<T> sourceType();

    /**
     * Returns the position of this factory within the resolution chain for its {@link
     * #sourceType()}; factories are tried in ascending order of priority. Lower numbers are
     * attempted first. Defaults to {@code 0}.
     *
     * @return the chain priority
     */
    default int priority() {
        return 0;
    }

    /**
     * Converts a source instance into a schema, or {@link Optional#empty()} if this factory does
     * not cover the given source.
     *
     * @param source the source representation, e.g. a JSON string, a parsed node, or a target
     *     class for a generation factory
     * @return the resulting schema, or empty if this factory cannot handle {@code source}
     * @throws IllegalArgumentException if this factory covers {@code source}'s type but {@code
     *     source} is not valid JSON
     */
    default Optional<JsonSchema> toJsonSchema(T source) {
        return Optional.empty();
    }
}
