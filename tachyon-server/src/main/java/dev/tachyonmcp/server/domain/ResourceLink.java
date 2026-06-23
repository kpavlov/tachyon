/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A reference to another resource, embedded within a content block.
 *
 * <p>Unlike {@link EmbeddedResource}, this is a lightweight pointer — it carries only
 * metadata (URI, name, title, description, MIME type) without the actual content data.
 */
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

    /** Creates a resource link with no optional fields. */
    static ResourceLink of(String uri, String name) {
        return builder(uri, name).build();
    }

    /** Creates a resource link with MIME type and no other optional fields. */
    static ResourceLink of(String uri, String name, @Nullable String mimeType) {
        return builder(uri, name).mimeType(mimeType).build();
    }

    /** Creates a builder for a resource link with the required fields. */
    static Builder builder(String uri, String name) {
        return new Builder(uri, name);
    }

    final class Builder {
        private final String uri;
        private final String name;
        private @Nullable String title;
        private @Nullable List<Icon> icons;
        private @Nullable String description;
        private @Nullable String mimeType;
        private @Nullable Annotations annotations;
        private @Nullable Double size;
        private @Nullable Map<String, JsonNode> meta;

        private Builder(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        public Builder icons(@Nullable List<Icon> icons) {
            this.icons = icons;
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder mimeType(@Nullable String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder annotations(@Nullable Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        public Builder size(@Nullable Double size) {
            this.size = size;
            return this;
        }

        public Builder meta(@Nullable Map<String, JsonNode> meta) {
            this.meta = meta;
            return this;
        }

        public ResourceLink build() {
            return new DefaultResourceLink(name, title, icons, uri, description, mimeType, annotations, size, meta);
        }
    }
}
