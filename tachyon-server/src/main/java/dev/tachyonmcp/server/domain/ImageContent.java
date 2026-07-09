/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * An image provided to or from an LLM.
 *
 * <p>The image data is base64-encoded in {@code data}, with the corresponding
 * {@code mimeType} describing the format (e.g. {@code image/png}, {@code image/jpeg}).
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface ImageContent extends ContentBlock {

    String data();

    String mimeType();

    @Nullable
    Annotations annotations();

    @Nullable
    Map<String, JsonNode> meta();

    @Override
    default Type type() {
        return Type.IMAGE;
    }

    static DefaultImageContent.Builder builder() {
        return DefaultImageContent.builder();
    }

    /** Creates an image content block with no metadata or annotations. */
    static ImageContent of(String data, String mimeType) {
        return DefaultImageContent.of(data, mimeType, null, null);
    }

    /** Creates an image content block with given annotations and no metadata. */
    static ImageContent of(String data, String mimeType, @Nullable Annotations annotations) {
        return DefaultImageContent.of(data, mimeType, annotations, null);
    }

    /** Creates an image content block with metadata and optional annotations. */
    static ImageContent of(
            String data, String mimeType, @Nullable Annotations annotations, @Nullable Map<String, JsonNode> meta) {
        return DefaultImageContent.of(data, mimeType, annotations, meta);
    }
}
