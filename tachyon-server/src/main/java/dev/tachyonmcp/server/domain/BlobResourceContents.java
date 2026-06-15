/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * Binary resource contents, encoded as a base64 string.
 *
 * <p>Used when a resource cannot be represented as text. The {@code uri} identifies the
 * resource, {@code mimeType} describes the binary format, and {@code blob} carries the
 * base64-encoded data.
 */
public record BlobResourceContents(String uri, @Nullable String mimeType, String blob) implements ResourceContents {}
