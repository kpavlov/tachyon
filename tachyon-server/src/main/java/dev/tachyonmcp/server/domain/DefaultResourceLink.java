/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultResourceLink(
        String name,
        @Nullable String title,
        @Nullable List<Icon> icons,
        String uri,
        @Nullable String description,
        @Nullable String mimeType,
        @Nullable Annotations annotations,
        @Nullable Double size,
        @Nullable Map<String, JsonNode> meta)
        implements ResourceLink {

    DefaultResourceLink {
        if (icons != null) {
            icons = java.util.Collections.unmodifiableList(icons);
        }
        if (meta != null) {
            meta = java.util.Collections.unmodifiableMap(meta);
        }
    }

    @Override
    public Type type() {
        return Type.RESOURCE_LINK;
    }
}
