/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.domain.HasMeta;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.TextContent;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ToolResult extends HasMeta {

    List<ContentBlock> content();

    @Nullable
    Boolean isError();

    @Nullable
    Map<String, JsonNode> structuredContent();

    @Nullable
    Map<String, JsonNode> meta();

    @Nullable
    Map<String, InputRequest> inputRequests();

    @Nullable
    String requestState();

    static DefaultToolResult.Builder builder() {
        return DefaultToolResult.builder();
    }

    static ToolResult text(String text) {
        return DefaultToolResult.of(List.of(TextContent.of(text)), null, null, null, null, null);
    }

    static ToolResult error(String message) {
        return DefaultToolResult.of(List.of(TextContent.of(message)), true, null, null, null, null);
    }

    static ToolResult of(List<ContentBlock> content) {
        return DefaultToolResult.of(content, null, null, null, null, null);
    }

    static ToolResult of(ContentBlock... content) {
        return DefaultToolResult.of(List.of(content), null, null, null, null, null);
    }

    static ToolResult empty() {
        return DefaultToolResult.of(List.of(), null, null, null, null, null);
    }

    static ToolResult inputRequired(Map<String, ? extends InputRequest> inputRequests, @Nullable String requestState) {
        return DefaultToolResult.builder()
                .content(List.of())
                .inputRequests(inputRequests)
                .requestState(requestState)
                .build();
    }
}
