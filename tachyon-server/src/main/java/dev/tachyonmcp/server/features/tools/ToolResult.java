/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.domain.TextContent;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record ToolResult(
        List<ContentBlock> content,
        @Nullable Boolean isError,
        @Nullable Map<String, JsonNode> structuredContent,
        @Nullable Map<String, JsonNode> meta) {

    public static ToolResult text(String text) {
        return new ToolResult(List.of(new TextContent(text, null)), null, null, null);
    }

    public static ToolResult error(String message) {
        return new ToolResult(List.of(new TextContent(message, null)), true, null, null);
    }

    public static ToolResult of(List<ContentBlock> content) {
        return new ToolResult(content, null, null, null);
    }
}
