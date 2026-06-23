/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultTextResourceContents(
        String text,
        String uri,
        @Nullable String mimeType,
        @Nullable Map<String, JsonNode> meta) implements TextResourceContents {

    DefaultTextResourceContents {
        if (meta != null) {
            meta = java.util.Collections.unmodifiableMap(meta);
        }
    }
}
