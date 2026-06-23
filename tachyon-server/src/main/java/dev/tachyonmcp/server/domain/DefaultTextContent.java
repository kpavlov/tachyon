/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultTextContent(
        String text,
        @Nullable Map<String, JsonNode> meta,
        @Nullable Annotations annotations) implements TextContent {

    DefaultTextContent {
        if (meta != null) {
            meta = java.util.Collections.unmodifiableMap(meta);
        }
    }

    @Override
    public Type type() {
        return Type.TEXT;
    }
}
