/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultToolDescriptor(
        String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable JsonNode inputSchema,
        @Nullable JsonNode outputSchema,
        @Nullable TaskSupport taskSupport,
        @Nullable ToolAnnotations annotations,
        @Nullable List<Icon> icons,
        @Nullable String extensionId)
        implements ToolDescriptor {}
