/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.PromptArgument;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultPromptDescriptor(
        String name,
        @Nullable String description,
        @Nullable String title,
        @Nullable List<PromptArgument> arguments,
        @Nullable JsonNode inputSchema,
        @Nullable List<Icon> icons,
        @Nullable String extensionId)
        implements PromptDescriptor {}
