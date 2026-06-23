/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ContentBlockMappers;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceTemplate;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.TextResourceContents;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

record DefaultResourceTemplateEntry(
        String name,
        String uriTemplate,
        @Nullable String description,
        @Nullable String mimeType,
        @Nullable String title,
        @Nullable Annotations annotations,
        @Nullable List<Icon> icons,
        Function<String, TextResourceContents> resolver)
        implements ResourceTemplateEntry {

    @Override
    public ResourceTemplate toModel() {
        return new ResourceTemplate(
                uriTemplate,
                description,
                mimeType,
                ContentBlockMappers.toProtocolAnnotations(annotations),
                null,
                name,
                title,
                ContentBlockMappers.toProtocolIcons(icons));
    }
}
