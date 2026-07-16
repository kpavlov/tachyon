/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolResult;
import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.domain.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class McpResponseMapperTest {

    private static final String RELATED_TASK = "io.modelcontextprotocol/related-task";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final McpResponseMapper mapper = new McpResponseMapper();

    @Test
    void completedTaskPayloadIsCallToolResultWithContentAndRelatedTask() {
        var result = new TaskResult.Completed(List.<ContentBlock>of(TextContent.of("done")), null, null);

        var payload = (CallToolResult) mapper.getTaskPayloadResult(result, "task-1");

        assertThat(payload.isError()).isNull();
        assertThat(payload.content()).hasSize(1);
        assertThat(relatedTaskId(payload)).isEqualTo("task-1");
    }

    @Test
    void completedTaskPayloadCarriesStructuredContent() {
        var structured = JSON.objectNode().put("temp", 72);
        var result = new TaskResult.Completed(List.of(), structured, null);

        var payload = (CallToolResult) mapper.getTaskPayloadResult(result, "task-2");

        assertThat(payload.structuredContent()).containsKey("temp");
        // A structured-only result injects the serialized JSON as a text block (MCP backwards-compat).
        assertThat(payload.content()).isNotEmpty();
    }

    @Test
    void failedTaskPayloadSetsIsErrorTrue() {
        var result = new TaskResult.Failed(List.<ContentBlock>of(TextContent.of("boom")), null, null);

        var payload = (CallToolResult) mapper.getTaskPayloadResult(result, "task-3");

        assertThat(payload.isError()).isTrue();
        assertThat(payload.content()).hasSize(1);
        assertThat(relatedTaskId(payload)).isEqualTo("task-3");
    }

    @Test
    void nullResultYieldsEmptyContentWithRelatedTask() {
        var payload = (CallToolResult) mapper.getTaskPayloadResult(null, "task-4");

        assertThat(payload.content()).isEmpty();
        assertThat(payload.isError()).isNull();
        assertThat(relatedTaskId(payload)).isEqualTo("task-4");
    }

    @Test
    void userMetaIsPreservedAlongsideRelatedTask() {
        var userMeta = Map.<String, JsonNode>of("trace", JSON.stringNode("abc"));
        var result = new TaskResult.Completed(List.<ContentBlock>of(TextContent.of("ok")), null, userMeta);

        var payload = (CallToolResult) mapper.getTaskPayloadResult(result, "task-5");

        assertThat(payload._meta()).containsEntry("trace", JSON.stringNode("abc"));
        assertThat(relatedTaskId(payload)).isEqualTo("task-5");
    }

    private static String relatedTaskId(CallToolResult payload) {
        JsonNode meta = payload._meta().get(RELATED_TASK);
        assertThat(meta).isNotNull();
        return meta.get("taskId").asString();
    }
}
