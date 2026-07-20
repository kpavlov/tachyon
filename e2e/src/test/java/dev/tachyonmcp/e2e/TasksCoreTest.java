/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ClientCapabilities;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Implementation;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.server.config.CapabilitiesConfig;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.features.tasks.DefaultTaskRegistry;
import dev.tachyonmcp.server.features.tasks.TaskState;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Core-path task behavior over the wire, driven through the public {@code TaskRegistry}/{@code Task}
 * API only. Extension-specific flows (the {@code create_task} tool, {@code task://} resource,
 * notification delivery) live in {@link TasksExtensionTest}.
 */
class TasksCoreTest extends AbstractStatefulMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.capabilities(CapabilitiesConfig.Builder::tasks));
    }

    // ── Capability advertisement ──────────────────────────────────────────

    @Test
    void advertisesTasksAsCoreCapability() throws Exception {
        startDefaultServer();
        try (var client = createTestClient()) {
            var response = client.post(null, buildInitializeJson(Map.of()));
            client.sendInitialized(
                    response.headers().firstValue("MCP-Session-Id").orElseThrow());

            assertThatJson(response.body())
                    .inPath("$.result.capabilities.tasks")
                    .isObject();
        }
    }

    // ── Core lifecycle (create / list / get / cancel / result) ────────────

    @Test
    void createsListsAndGetsTask() throws Exception {
        startDefaultServer();
        try (var client = createTestClient()) {
            client.initialize();
            var task = server.tasks().create();

            var listJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/list","params":{}}
                    """);
            assertThatJson(listJson).inPath("$.result.tasks.length()").isEqualTo(1);

            var getJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(task.id()));
            assertThatJson(getJson).inPath("$.result.taskId").isEqualTo(task.id());
            assertThatJson(getJson).inPath("$.result.status").isEqualTo("working");
        }
    }

    @Test
    void statusMessageReflectsCallerSuppliedTextNotTheBareStatusName() throws Exception {
        // Regression test: the wire mapper used to hardcode statusMessage to entry.status().toString(),
        // discarding whatever message updateStatus/complete callers actually set.
        startDefaultServer();
        try (var client = createTestClient()) {
            client.initialize();
            var task = server.tasks().create();
            var registry = (DefaultTaskRegistry) server.tasks();
            registry.updateStatus(task.id(), TaskState.WORKING, "step 1 of 3");

            var workingJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(task.id()));
            assertThatJson(workingJson).inPath("$.result.statusMessage").isEqualTo("step 1 of 3");

            task.complete(
                    TaskResult.completed(JsonNodeFactory.instance.objectNode().put("output", "success")));

            var completedJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(task.id()));
            assertThatJson(completedJson).inPath("$.result.status").isEqualTo("completed");
            assertThatJson(completedJson).inPath("$.result.statusMessage").isEqualTo("step 1 of 3");
        }
    }

    @Test
    void cancelsTask() throws Exception {
        startDefaultServer();
        try (var client = createTestClient()) {
            client.initialize();
            var task = server.tasks().create();

            var cancelJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/cancel","params":{"taskId":"%s"}}
                    """.formatted(task.id()));
            assertThatJson(cancelJson).inPath("$.result.taskId").isEqualTo(task.id());
            assertThatJson(cancelJson).inPath("$.result.status").isEqualTo("cancelled");
        }
    }

    @Test
    void completedTaskResultIsACallToolResult() throws Exception {
        startDefaultServer();
        try (var client = createTestClient()) {
            client.initialize();
            var task = server.tasks().create();
            var output = JsonNodeFactory.instance.objectNode().put("output", "success");
            task.complete(TaskResult.completed(output));

            var getJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(task.id()));
            assertThatJson(getJson).inPath("$.result.status").isEqualTo("completed");

            // tasks/result returns exactly what the tool call would have — a CallToolResult
            // (content + structuredContent), not a wrapped {"result": …} envelope.
            var resultJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/result","params":{"taskId":"%s"}}
                    """.formatted(task.id()));
            assertThatJson(resultJson).inPath("$.result.content").isArray();
            assertThatJson(resultJson)
                    .inPath("$.result.structuredContent.output")
                    .isEqualTo("success");
        }
    }

    @Test
    void unknownTaskReturnsError() throws Exception {
        startDefaultServer();
        try (var client = createTestClient()) {
            client.initialize();

            var getJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"nonexistent-id"}}
                    """);
            assertThatJson(getJson).inPath("$.error.code").isEqualTo(-32602);
        }
    }

    private static String buildInitializeJson(Map<String, JsonNode> extensions) {
        var capsBuilder = ClientCapabilities.builder();
        if (!extensions.isEmpty()) {
            capsBuilder.extensions(extensions);
        }
        var params = InitializeRequestParams.builder()
                .protocolVersion("2025-11-25")
                .capabilities(capsBuilder.build())
                .clientInfo(new Implementation("1.0", null, null, "test-client", null, null))
                .build();
        try {
            var paramsJson = new ObjectMapper().writeValueAsString(params);
            return """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":%s}
                    """.formatted(paramsJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
