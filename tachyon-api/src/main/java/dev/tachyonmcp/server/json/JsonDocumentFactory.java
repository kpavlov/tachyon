/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

/**
 * Creates a {@link JsonDocument} from a source representation, validating it in the process.
 *
 * <p>Unlike {@link JsonDocument#of(String)}, which wraps a string without inspecting it, a
 * factory implementation parses {@code source} and rejects malformed JSON.
 *
 * @param <T> the source representation type
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface JsonDocumentFactory<T> {

    /**
     * Creates a document from {@code source}.
     *
     * @param source the source representation
     * @return the parsed document
     * @throws IllegalArgumentException if {@code source} is not valid JSON
     */
    JsonDocument toJsonDocument(T source);
}
