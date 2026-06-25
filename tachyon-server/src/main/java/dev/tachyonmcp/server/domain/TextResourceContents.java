/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Text-based resource contents returned by a resource handler.
 *
 * <p>The {@code uri} identifies the originating resource, and {@code text} carries
 * the actual content. {@code mimeType} should be set when the text has a specific
 * format (e.g. {@code application/json}, {@code text/markdown}).
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface TextResourceContents extends ResourceContents {

    String text();

    static TextResourceContents of(String uri, @Nullable String mimeType, String text) {
        return DefaultTextResourceContents.of(null, uri, mimeType, text);
    }

    static TextResourceContents of(
            String uri, @Nullable String mimeType, String text, @Nullable Map<String, JsonNode> meta) {
        return DefaultTextResourceContents.of(meta, uri, mimeType, text);
    }
}
