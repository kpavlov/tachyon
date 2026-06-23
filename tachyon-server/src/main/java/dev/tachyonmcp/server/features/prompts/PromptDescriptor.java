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

public interface PromptDescriptor extends McpResourceType {

    String name();

    @Nullable
    String description();

    @Nullable
    String title();

    @Nullable
    List<PromptArgument> arguments();

    @Nullable
    JsonNode inputSchema();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    static PromptDescriptor of(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonNode inputSchema) {
        return new DefaultPromptDescriptor(name, description, title, arguments, inputSchema, null, null);
    }

    static PromptDescriptor of(
            String name,
            @Nullable String description,
            @Nullable String title,
            @Nullable List<PromptArgument> arguments,
            @Nullable JsonNode inputSchema,
            @Nullable List<Icon> icons) {
        return new DefaultPromptDescriptor(name, description, title, arguments, inputSchema, icons, null);
    }

    static PromptDescriptor of(String name, String description) {
        return new DefaultPromptDescriptor(name, description, null, null, null, null, null);
    }
}
