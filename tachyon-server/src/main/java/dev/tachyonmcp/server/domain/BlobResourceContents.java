/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Binary resource contents, encoded as a base64 string.
 *
 * <p>Used when a resource cannot be represented as text. The {@code uri} identifies the
 * resource, {@code mimeType} describes the binary format, and {@code blob} carries the
 * base64-encoded data.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface BlobResourceContents extends ResourceContents {

    String blob();

    static BlobResourceContents of(String uri, @Nullable String mimeType, String blob) {
        return DefaultBlobResourceContents.of(null, uri, mimeType, blob);
    }

    static BlobResourceContents of(String uri, @Nullable String mimeType, String blob, Map<String, JsonNode> meta) {
        return DefaultBlobResourceContents.of(meta, uri, mimeType, blob);
    }
}
