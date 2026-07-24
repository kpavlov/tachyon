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

    /** Returns the encoded JSON value. */
    String json();

    /**
     * Returns the retained provider-specific representation when it matches {@code type}.
     *
     * <p>The default implementation is provider-neutral and returns an empty optional.
     */
    default <T> Optional<T> unwrap(Class<T> type) {
        return Optional.empty();
    }

    /** Creates a document from encoded JSON. */
    static JsonDocument of(String json) {
        return new DefaultJsonDocument(JsonDocuments.requireContent(json));
    }
}
