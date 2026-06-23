/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface ResourceDescriptor extends McpResourceType {

    String name();

    String uri();

    @Nullable
    String description();

    @Nullable
    String mimeType();

    @Nullable
    String title();

    @Nullable
    Annotations annotations();

    @Nullable
    Double size();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    static ResourceDescriptor of(String name, String uri, @Nullable String description, @Nullable String mimeType) {
        return new DefaultResourceDescriptor(name, uri, description, mimeType, null, null, null, null, null);
    }

    static ResourceDescriptor of(
            String name,
            String uri,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable Double size,
            @Nullable List<Icon> icons) {
        return new DefaultResourceDescriptor(name, uri, description, mimeType, title, annotations, size, icons, null);
    }
}
