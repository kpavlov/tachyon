/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A reference to another resource, embedded within a content block.
 *
 * <p>Unlike {@link EmbeddedResource}, this is a lightweight pointer — it carries only
 * metadata (URI, name, title, description, MIME type) without the actual content data.
 */
@Value.Immutable
@Value.Style(allParameters = true, typeImmutable = "Default*")
public non-sealed interface ResourceLink extends ContentBlock {

    String name();

    @Nullable
    String title();

    @Nullable
    List<Icon> icons();

    String uri();

    @Nullable
    String description();

    @Nullable
    String mimeType();

    @Nullable
    Annotations annotations();

    @Nullable
    Double size();

    @Nullable
    Map<String, JsonNode> meta();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uri().isBlank()) throw new IllegalArgumentException("uri must not be blank");
        if (size() != null && size() < 0) throw new IllegalArgumentException("size must be >= 0, got: " + size());
    }

    @Override
    default Type type() {
        return Type.RESOURCE_LINK;
    }

    /** Creates a resource link with no optional fields. */
    static ResourceLink of(String uri, String name) {
        return builder(uri, name).build();
    }

    /** Creates a resource link with MIME type and no other optional fields. */
    static ResourceLink of(String uri, String name, @Nullable String mimeType) {
        return builder(uri, name).mimeType(mimeType).build();
    }

    static DefaultResourceLink.Builder builder() {
        return DefaultResourceLink.builder();
    }

    /** Creates a builder for a resource link with the required fields. */
    static DefaultResourceLink.Builder builder(String uri, String name) {
        return builder().uri(uri).name(name);
    }
}
