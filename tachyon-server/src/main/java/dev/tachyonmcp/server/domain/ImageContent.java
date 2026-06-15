/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * An image provided to or from an LLM.
 *
 * <p>The image data is base64-encoded in {@code data}, with the corresponding
 * {@code mimeType} describing the format (e.g. {@code image/png}, {@code image/jpeg}).
 */
public record ImageContent(
        String data, String mimeType, @Nullable Annotations annotations) implements ContentBlock {}
