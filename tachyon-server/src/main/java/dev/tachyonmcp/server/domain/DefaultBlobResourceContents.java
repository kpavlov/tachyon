/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultBlobResourceContents(
        String blob,
        String uri,
        @Nullable String mimeType,
        @Nullable Map<String, JsonNode> meta) implements BlobResourceContents {

    DefaultBlobResourceContents {
        if (meta != null) {
            meta = java.util.Collections.unmodifiableMap(meta);
        }
    }
}
