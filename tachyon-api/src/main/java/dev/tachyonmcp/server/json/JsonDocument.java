/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import java.util.Optional;

/**
 * An encoded JSON value.
 *
 * <p>The server validates and parses the JSON when it crosses a runtime boundary.
 * Implementations may retain a stable provider-specific representation and expose it through
 * {@link #unwrap(Class)}. The encoded and retained representations must describe the same value.
 *
 * @author Konstantin Pavlov
 */
public interface JsonDocument {

    /**
     * Returns the encoded JSON value.
     *
     * @return the JSON string
     */
    String json();

    /**
     * Returns the retained provider-specific representation when it matches {@code type}.
     *
     * <p>The default implementation is provider-neutral and returns an empty optional.
     *
     * @param <T>  the requested representation type
     * @param type the class of the requested representation
     * @return the provider-specific representation, or empty if not available
     */
    default <T> Optional<T> unwrap(Class<T> type) {
        return Optional.empty();
    }

    /**
     * Creates a document from encoded JSON.
     *
     * @param json the JSON string
     * @return a new document
     */
    static JsonDocument of(String json) {
        return new DefaultJsonDocument(JsonDocuments.requireContent(json));
    }
}
