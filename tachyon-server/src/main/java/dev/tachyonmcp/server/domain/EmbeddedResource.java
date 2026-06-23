/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A complete resource embedded inline within a tool result, prompt, or other content.
 *
 * <p>The embedded {@code resource} contains both the URI and the actual content
 * ({@link TextResourceContents} or {@link BlobResourceContents}), allowing the
 * server to attach resource data directly without requiring a separate read round-trip.
 */
public non-sealed interface EmbeddedResource extends ContentBlock {

    ResourceContents resource();

    @Nullable
    Map<String, JsonNode> meta();

    @Nullable
    Annotations annotations();

    /** Creates an embedded resource with no metadata or annotations. */
    static EmbeddedResource of(ResourceContents resource) {
        return new DefaultEmbeddedResource(resource, null, null);
    }

    /** Creates an embedded resource with given annotations and no metadata. */
    static EmbeddedResource of(ResourceContents resource, @Nullable Annotations annotations) {
        return new DefaultEmbeddedResource(resource, null, annotations);
    }

    /** Creates an embedded resource with metadata and optional annotations. */
    static EmbeddedResource of(
            ResourceContents resource, @Nullable Map<String, JsonNode> meta, @Nullable Annotations annotations) {
        return new DefaultEmbeddedResource(resource, meta, annotations);
    }
}
