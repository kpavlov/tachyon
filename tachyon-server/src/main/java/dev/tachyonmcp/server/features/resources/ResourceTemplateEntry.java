/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.TextResourceContents;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public interface ResourceTemplateEntry extends McpResourceType {

    String name();

    String uriTemplate();

    @Nullable
    String description();

    @Nullable
    String mimeType();

    @Nullable
    String title();

    @Nullable
    Annotations annotations();

    @Nullable
    List<Icon> icons();

    Function<String, TextResourceContents> resolver();

    static ResourceTemplateEntry of(
            String name,
            String uriTemplate,
            @Nullable String description,
            @Nullable String mimeType,
            Function<String, TextResourceContents> resolver) {
        return new DefaultResourceTemplateEntry(name, uriTemplate, description, mimeType, null, null, null, resolver);
    }

    static ResourceTemplateEntry of(
            String name,
            String uriTemplate,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable List<Icon> icons,
            Function<String, TextResourceContents> resolver) {
        return new DefaultResourceTemplateEntry(
                name, uriTemplate, description, mimeType, title, annotations, icons, resolver);
    }
}
