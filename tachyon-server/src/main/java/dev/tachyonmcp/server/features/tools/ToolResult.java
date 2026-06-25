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

public interface ToolResult {

    List<ContentBlock> content();

    @Nullable
    Boolean isError();

    @Nullable
    Map<String, JsonNode> structuredContent();

    @Nullable
    Map<String, JsonNode> meta();

    static ToolResult text(String text) {
        return new DefaultToolResult(List.of(TextContent.of(text)), null, null, null);
    }

    static ToolResult error(String message) {
        return new DefaultToolResult(List.of(TextContent.of(message)), true, null, null);
    }

    static ToolResult of(List<ContentBlock> content) {
        return new DefaultToolResult(content, null, null, null);
    }

    static ToolResult of(ContentBlock... content) {
        return new DefaultToolResult(List.of(content), null, null, null);
    }

    static ToolResult from(Object result) {
        if (result instanceof ToolResult r) return r;
        var text = TextContent.of(result != null ? result.toString() : "");
        return new DefaultToolResult(List.of(text), null, null, null);
    }
}
