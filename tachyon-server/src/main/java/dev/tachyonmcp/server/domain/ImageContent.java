/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * An image provided to or from an LLM.
 *
 * <p>The image data is base64-encoded in {@code data}, with the corresponding
 * {@code mimeType} describing the format (e.g. {@code image/png}, {@code image/jpeg}).
 */
public non-sealed interface ImageContent extends ContentBlock {

    String data();

    String mimeType();

    @Nullable
    Map<String, JsonNode> meta();

    @Nullable
    Annotations annotations();

    /** Creates an image content block with no metadata or annotations. */
    static ImageContent of(String data, String mimeType) {
        return new DefaultImageContent(data, mimeType, null, null);
    }

    /** Creates an image content block with given annotations and no metadata. */
    static ImageContent of(String data, String mimeType, @Nullable Annotations annotations) {
        return new DefaultImageContent(data, mimeType, null, annotations);
    }

    /** Creates an image content block with metadata and optional annotations. */
    static ImageContent of(
            String data, String mimeType, @Nullable Map<String, JsonNode> meta, @Nullable Annotations annotations) {
        return new DefaultImageContent(data, mimeType, meta, annotations);
    }
}
