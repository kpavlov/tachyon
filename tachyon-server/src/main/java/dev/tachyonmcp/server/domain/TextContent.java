/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A plain-text content block provided to or from an LLM.
 *
 * <p>Text is the most common content type. Optional {@link Annotations} allow the
 * server to hint at audience, priority, or modification time.
 */
public non-sealed interface TextContent extends ContentBlock {

    String text();

    @Nullable
    Map<String, JsonNode> meta();

    @Nullable
    Annotations annotations();

    /** Creates a text content block with no metadata or annotations. */
    static TextContent of(String text) {
        return new DefaultTextContent(text, null, null);
    }

    /** Creates a text content block with given annotations and no metadata. */
    static TextContent of(String text, @Nullable Annotations annotations) {
        return new DefaultTextContent(text, null, annotations);
    }

    /** Creates a text content block with metadata and optional annotations. */
    static TextContent of(String text, @Nullable Map<String, JsonNode> meta, @Nullable Annotations annotations) {
        return new DefaultTextContent(text, meta, annotations);
    }
}
