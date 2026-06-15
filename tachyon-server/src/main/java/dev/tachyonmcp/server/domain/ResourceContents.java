/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import dev.tachyonmcp.server.features.resources.ResourceRegistry;
import org.jspecify.annotations.Nullable;

/**
 * The actual content of a resource, either text or binary.
 *
 * <p>Both variants share a {@code uri} and {@code mimeType}. The sealed hierarchy lets
 * {@link ResourceRegistry} and mappers
 * handle each variant explicitly.
 */
public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {
    String uri();

    @Nullable
    String mimeType();
}
