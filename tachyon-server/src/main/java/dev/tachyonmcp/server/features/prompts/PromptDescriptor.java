/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.PromptArgument;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record PromptDescriptor(
        String name,
        @Nullable String description,
        @Nullable String title,
        @Nullable List<PromptArgument> arguments,
        @Nullable JsonNode inputSchema,
        @Nullable List<Icon> icons,
        @Nullable String extensionId)
        implements McpResourceType {

    public PromptDescriptor(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonNode inputSchema) {
        this(name, description, title, arguments, inputSchema, null, null);
    }

    public PromptDescriptor(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonNode inputSchema,
            @Nullable List<Icon> icons) {
        this(name, description, title, arguments, inputSchema, icons, null);
    }

    public static PromptDescriptor of(String name, String description) {
        return new PromptDescriptor(name, description, null, null, null, null, null);
    }
}
