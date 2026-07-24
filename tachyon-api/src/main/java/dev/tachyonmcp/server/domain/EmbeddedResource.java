/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * A complete resource embedded inline within a tool result, prompt, or other content.
 *
 * <p>The embedded {@code resource} contains both the URI and the actual content
 * ({@link TextResourceContents} or {@link BlobResourceContents}), allowing the
 * server to attach resource data directly without requiring a separate read round-trip.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface EmbeddedResource extends ContentBlock, HasMeta {

    ResourceContents resource();

    @Nullable
    Annotations annotations();

    @Nullable
    Map<String, Object> meta();

    @Override
    default Type type() {
        return Type.RESOURCE;
    }

    static Builder builder() {
        return DefaultEmbeddedResource.builder();
    }

    /** Creates an embedded resource with no metadata or annotations. */
    static EmbeddedResource of(ResourceContents resource) {
        return DefaultEmbeddedResource.of(resource, null, null);
    }

    /** Creates an embedded resource with given annotations and no metadata. */
    static EmbeddedResource of(ResourceContents resource, @Nullable Annotations annotations) {
        return DefaultEmbeddedResource.of(resource, annotations, null);
    }

    /** Creates an embedded resource with metadata and optional annotations. */
    static EmbeddedResource of(
            ResourceContents resource, @Nullable Annotations annotations, @Nullable Map<String, Object> meta) {
        return DefaultEmbeddedResource.of(resource, annotations, meta);
    }

    interface Builder {
        Builder resource(ResourceContents resource);

        Builder annotations(@Nullable Annotations annotations);

        Builder meta(@Nullable Map<String, ?> entries);

        EmbeddedResource build();
    }
}
