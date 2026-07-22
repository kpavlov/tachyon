/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Binary resource contents, encoded as a base64 string.
 *
 * <p>Used when a resource cannot be represented as text. The {@code uri} identifies the
 * resource, {@code mimeType} describes the binary format, and {@code blob} carries the
 * base64-encoded data.
 */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
public non-sealed interface BlobResourceContents extends ResourceContents {

    @Override
    @Value.Parameter(order = 1)
    String uri();

    @Override
    @Nullable
    @Value.Parameter(order = 2)
    String mimeType();

    @Value.Parameter(order = 3)
    String blob();

    @Override
    @Nullable
    @Value.Parameter(order = 4)
    Map<String, JsonNode> meta();

    @Value.Check
    default void check() {
        if (uri().isBlank()) throw new IllegalArgumentException("uri must not be blank");
        if (blob().isBlank()) throw new IllegalArgumentException("blob must not be blank");
    }

    /**
     * Creates binary resource contents with no {@code _meta}.
     *
     * @param uri      the resource URI
     * @param blob     the base64-encoded binary content
     * @param mimeType the content's MIME type, or {@code null} if unspecified
     */
    static BlobResourceContents of(String uri, String blob, @Nullable String mimeType) {
        return DefaultBlobResourceContents.of(uri, mimeType, blob, null);
    }

    /**
     * Creates binary resource contents.
     *
     * @param uri      the resource URI
     * @param blob     the base64-encoded binary content
     * @param mimeType the content's MIME type, or {@code null} if unspecified
     * @param meta     the {@code _meta} entries, or {@code null} if none
     */
    static BlobResourceContents of(
            String uri, String blob, @Nullable String mimeType, @Nullable Map<String, JsonNode> meta) {
        return DefaultBlobResourceContents.of(uri, mimeType, blob, meta);
    }

    static Builder builder() {
        return DefaultBlobResourceContents.builder();
    }

    interface Builder {
        Builder uri(String uri);

        Builder blob(String blob);

        Builder mimeType(@Nullable String mimeType);

        Builder meta(@Nullable Map<String, ? extends JsonNode> entries);

        BlobResourceContents build();
    }
}
