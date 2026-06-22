/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceTemplate;
import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.TextResourceContents;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public record ResourceTemplateEntry(
        String name,
        String uriTemplate,
        @Nullable String description,
        @Nullable String mimeType,
        @Nullable String title,
        @Nullable Annotations annotations,
        @Nullable List<Icon> icons,
        Function<String, TextResourceContents> resolver)
        implements McpResourceType {

    public ResourceTemplateEntry(
            String name,
            String uriTemplate,
            @Nullable String description,
            @Nullable String mimeType,
            Function<String, TextResourceContents> resolver) {
        this(name, uriTemplate, description, mimeType, null, null, null, resolver);
    }

    public ResourceTemplate toModel() {
        return new ResourceTemplate(
                uriTemplate,
                description,
                mimeType,
                McpResourceMapper.toProtocolAnnotations(annotations),
                null,
                name,
                title,
                McpResourceMapper.toProtocolIcons(icons));
    }
}
