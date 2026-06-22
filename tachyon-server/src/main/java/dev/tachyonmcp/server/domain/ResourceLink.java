/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * A reference to another resource, embedded within a content block.
 *
 * <p>Unlike {@link EmbeddedResource}, this is a lightweight pointer — it carries only
 * metadata (URI, name, title, description, MIME type) without the actual content data.
 */
public record ResourceLink(
        String uri,
        String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable String mimeType,
        @Nullable Annotations annotations)
        implements ContentBlock {}
