/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.json;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.json.spi.JsonDocumentFactory;
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
    @ExperimentalApi
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

    /**
     * Creates a document from encoded JSON, validating it via the {@link
     * JsonDocumentFactory} discovered through {@link
     * java.util.ServiceLoader} for {@link String}.
     *
     * @param json the JSON string
     * @return the parsed document
     * @throws IllegalArgumentException if {@code json} is not valid JSON
     * @throws IllegalStateException if no {@code JsonDocumentFactory<String>} is registered
     */
    static JsonDocument parse(String json) {
        return from(json, String.class);
    }

    /**
     * Creates a document from an already-parsed representation, via the {@link
     * JsonDocumentFactory} discovered through {@link
     * java.util.ServiceLoader} for {@code type}.
     *
     * @param <T>    the source representation type
     * @param source the source representation, e.g. a Jackson {@code JsonNode}
     * @param type   the source representation's type
     * @return the parsed document
     * @throws IllegalStateException if no {@code JsonDocumentFactory<T>} is registered for {@code type}
     */
    static <T> JsonDocument from(T source, Class<T> type) {
        return JsonDocuments.from(source, type);
    }
}
