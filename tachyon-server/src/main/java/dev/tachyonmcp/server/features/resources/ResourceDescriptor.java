/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ResourceDescriptor(
        String name,
        String uri,
        @Nullable String description,
        @Nullable String mimeType,
        @Nullable String title,
        @Nullable Annotations annotations,
        @Nullable Double size,
        @Nullable List<Icon> icons,
        @Nullable String extensionId)
        implements McpResourceType {

    public ResourceDescriptor(String name, String uri, @Nullable String description, @Nullable String mimeType) {
        this(name, uri, description, mimeType, null, null, null, null, null);
    }

    public ResourceDescriptor(
            String name,
            String uri,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable Double size,
            @Nullable List<Icon> icons) {
        this(name, uri, description, mimeType, title, annotations, size, icons, null);
    }
}
