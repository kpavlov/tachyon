/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json.spi;

import dev.tachyonmcp.server.json.JsonDocument;

/**
 * Creates a {@link JsonDocument} from a source representation, validating it in the process.
 *
 * <p>Unlike {@link JsonDocument#of(String)}, which wraps a string without inspecting it, a
 * factory implementation parses {@code source} and rejects malformed JSON.
 *
 * <p>Discoverable via {@link java.util.ServiceLoader}: implementations register themselves in
 * {@code META-INF/services/dev.tachyonmcp.server.json.spi.JsonDocumentFactory}, self-reporting the
 * source type they accept through {@link #sourceType()} so {@link JsonDocument#from(Object, Class)}
 * can resolve the right provider for a given type.
 *
 * @param <T> the source representation type
 * @author Konstantin Pavlov
 */
public interface JsonDocumentFactory<T> {

    /**
     * Returns the source type this factory accepts.
     *
     * @return the source type
     */
    Class<T> sourceType();

    /**
     * Creates a document from {@code source}.
     *
     * @param source the source representation
     * @return the parsed document
     * @throws IllegalArgumentException if {@code source} is not valid JSON
     */
    JsonDocument toJsonDocument(T source);
}
