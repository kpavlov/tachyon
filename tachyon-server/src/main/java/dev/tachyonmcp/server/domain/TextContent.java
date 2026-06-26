/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A plain-text content block provided to or from an LLM.
 *
 * <p>Text is the most common content type. Optional {@link Annotations} allow the
 * server to hint at audience, priority, or modification time.
 */
@Value.Immutable
@Value.Builder
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface TextContent extends ContentBlock {

    String text();

    @Nullable
    Map<String, JsonNode> meta();

    @Nullable
    Annotations annotations();

    default ContentBlock.Type type() {
        return ContentBlock.Type.TEXT;
    }

    static DefaultTextContent.Builder builder() {
        return DefaultTextContent.builder();
    }

    /** Creates a text content block with no metadata or annotations. */
    static TextContent of(String text) {
        return DefaultTextContent.of(text, null, null);
    }

    /** Creates a text content block with given annotations and no metadata. */
    static TextContent of(String text, @Nullable Annotations annotations) {
        return DefaultTextContent.of(text, null, annotations);
    }

    /** Creates a text content block with metadata and optional annotations. */
    static TextContent of(String text, @Nullable Map<String, JsonNode> meta, @Nullable Annotations annotations) {
        return DefaultTextContent.of(text, meta, annotations);
    }
}
