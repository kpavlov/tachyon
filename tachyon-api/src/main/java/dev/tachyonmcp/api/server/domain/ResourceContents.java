/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * The actual content of a resource, either text or binary.
 *
 * <p>Both variants share a {@code uri}, {@code mimeType}, and {@code meta}. The sealed
 * hierarchy lets resource registries and mappers handle each variant explicitly.
 */
public sealed interface ResourceContents extends HasMeta permits TextResourceContents, BlobResourceContents {

    /**
     * Returns the resource URI.
     *
     * @return the resource identifier
     */
    String uri();

    /**
     * Returns the MIME type of the resource content, or {@code null} if unspecified.
     *
     * @return the MIME type, or {@code null}
     */
    @Nullable
    String mimeType();
}
