/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ContentBlock;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultToolResult(
        List<ContentBlock> content,
        @Nullable Boolean isError,
        @Nullable Map<String, JsonNode> structuredContent,
        @Nullable Map<String, JsonNode> meta)
        implements ToolResult {

    DefaultToolResult {
        content = List.copyOf(content);
    }
}
