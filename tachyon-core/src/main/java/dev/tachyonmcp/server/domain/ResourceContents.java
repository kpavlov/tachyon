/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * The actual content of a resource, either text or binary.
 *
 * <p>Both variants share a {@code uri}, {@code mimeType}, and {@code meta}. The sealed
 * hierarchy lets resource registries and mappers handle each variant explicitly.
 */
public sealed interface ResourceContents extends HasMeta permits TextResourceContents, BlobResourceContents {

    String uri();

    @Nullable
    String mimeType();
}
