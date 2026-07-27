/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.json.spi;

import dev.tachyonmcp.protocol.api.json.JsonSchema;

/**
 * Creates a {@link JsonSchema} from a source representation, validating it in the process.
 *
 * <p>Unlike {@link JsonSchema#of(String)}, which wraps a string without inspecting it, a
 * factory implementation parses {@code source} and rejects malformed JSON.
 *
 * <p>Discoverable via {@link java.util.ServiceLoader}: implementations register themselves in
 * {@code META-INF/services/dev.tachyonmcp.protocol.api.spi.json.JsonSchemaFactory}, self-reporting the
 * source type they accept through {@link #sourceType()} so {@link JsonSchema#from(Object, Class)}
 * can resolve the right provider for a given type.
 *
 * @param <T> the source representation type
 * @author Konstantin Pavlov
 */
public interface JsonSchemaFactory<T> {

    /**
     * Returns the source type this factory accepts.
     *
     * @return the source type
     */
    Class<T> sourceType();

    /**
     * Creates a schema from {@code source}.
     *
     * @param source the source representation
     * @return the parsed schema
     * @throws IllegalArgumentException if {@code source} is not valid JSON
     */
    JsonSchema toJsonSchema(T source);
}
