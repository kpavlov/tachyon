/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * Text-based resource contents returned by a resource handler.
 *
 * <p>The {@code uri} identifies the originating resource, and {@code text} carries
 * the actual content. {@code mimeType} should be set when the text has a specific
 * format (e.g. {@code application/json}, {@code text/markdown}).
 */
public record TextResourceContents(String uri, @Nullable String mimeType, String text) implements ResourceContents {}
