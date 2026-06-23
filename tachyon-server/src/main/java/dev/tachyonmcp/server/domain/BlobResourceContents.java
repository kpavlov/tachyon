/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Binary resource contents, encoded as a base64 string.
 *
 * <p>Used when a resource cannot be represented as text. The {@code uri} identifies the
 * resource, {@code mimeType} describes the binary format, and {@code blob} carries the
 * base64-encoded data.
 */
public non-sealed interface BlobResourceContents extends ResourceContents {

    String blob();

    static BlobResourceContents of(String uri, @Nullable String mimeType, String blob) {
        return new DefaultBlobResourceContents(blob, uri, mimeType, null);
    }

    static BlobResourceContents of(String uri, @Nullable String mimeType, String blob, Map<String, JsonNode> meta) {
        return new DefaultBlobResourceContents(blob, uri, mimeType, meta);
    }
}
