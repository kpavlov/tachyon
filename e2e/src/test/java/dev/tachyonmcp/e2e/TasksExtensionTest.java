/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ClientCapabilities;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Implementation;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.config.CapabilitiesConfig;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.features.tasks.TasksExtension;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JsonUtils;
import dev.tachyonmcp.server.session.DispatchContext;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * <a href="https://modelcontextprotocol.io/seps/1686-tasks">SEP-1686 Tasks</a> —
 * negotiable extension exposed only when client opts in via {@code initialize} capabilities.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD) // to fix shouldNotifyTaskStatusOnCreate flakiness
class TasksExtensionTest extends AbstractMcpE2eTest {

    private static final String TASKS_EXTENSION_ID = "io.modelcontextprotocol/tasks";

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.extension(TasksExtension.instance()));
    }

    @Test
    void advertisesTasksExtensionWhenNegotiated() throws Exception {
        startServer(it -> it.capabilities(CapabilitiesConfig.Builder::tasks).extension(TasksExtension.instance()));
        try (var client = createTestClient()) {
            var initBody = buildInitializeJson(Map.of(TASKS_EXTENSION_ID, JsonNodeFactory.instance.objectNode()));
            var response = client.post(null, initBody);
            client.sendInitialized(
                    response.headers().firstValue("MCP-Session-Id").orElseThrow());

            assertThatJson(response.body())
                    .inPath("$.result.capabilities.tasks")
                    .isObject();
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.extensions")
                    .isObject()
                    .containsKey(TASKS_EXTENSION_ID);
        }
    }

    @Test
    void createTaskViaTool() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = initializeWithExtension(client);

            var callJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_task","arguments":{"name":"my-task","description":"from tool"}}}
                    """);
            assertThatJson(callJson).inPath("$.result.content[0].text").isString();
            var taskId = extractText(callJson);

            var getJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(taskId));
            assertThatJson(getJson).inPath("$.result.taskId").isEqualTo(taskId);
            assertThatJson(getJson).inPath("$.result.status").isEqualTo("working");
        }
    }

    @Test
    void rejectsToolCallWhileSessionNotYetActive() throws Exception {
        // Deterministic counterpart to the createTaskViaTool flake: initialize a session but
        // deliberately skip notifications/initialized, so it stays INITIALIZING and the
        // activation gate (McpDispatcher) rejects the tools/call with a JSON-RPC error.
        // This is the exact response shape the flake produced when activation had not yet
        // completed — and it falsifies the diagnosis if initialize secretly activates.
        try (var client = createTestClient()) {
            @Language("json")
            var initBody = """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"extensions":{"io.modelcontextprotocol/tasks":{}}},"clientInfo":{"name":"test","version":"1.0"}}}
                    """;
            var initResponse = client.post(null, initBody);
            var sessionId = initResponse.headers().firstValue("MCP-Session-Id").orElseThrow();

            var response = client.post(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_task","arguments":{"name":"my-task"}}}
                    """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/json");
            assertThat(response.body()).contains("not yet active");
            assertThatJson(response.body()).inPath("$.error").isObject();
        }
    }

    @Test
    void createCompletePollAndGetResult() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = initializeWithExtension(client);

            var callJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_task","arguments":{"name":"full-cycle","description":"complete lifecycle"}}}
                    """);
            var taskId = extractText(callJson);
            assertThat(taskId).isNotEmpty();

            var getBeforeJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(taskId));
            assertThatJson(getBeforeJson).inPath("$.result.taskId").isEqualTo(taskId);
            assertThatJson(getBeforeJson).inPath("$.result.status").isEqualTo("working");

            server.tasks().get(taskId).complete(TaskResult.completed(JsonUtils.parse("{\"output\":\"completed\"}")));

            var getAfterJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":4,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(taskId));
            assertThatJson(getAfterJson).inPath("$.result.taskId").isEqualTo(taskId);
            assertThatJson(getAfterJson).inPath("$.result.status").isEqualTo("completed");

            var resultJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":5,"method":"tasks/result","params":{"taskId":"%s"}}
                    """.formatted(taskId));
            // tasks/result returns a CallToolResult: structured tool output lands in structuredContent.
            assertThatJson(resultJson)
                    .inPath("$.result.structuredContent")
                    .isObject()
                    .containsEntry("output", "completed");
        }
    }

    @Test
    void readTaskResourceTemplate() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = initializeWithExtension(client);

            var task = server.tasks().create();

            var readJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"task://%s"}}
                    """.formatted(task.id()));
            assertThatJson(readJson).inPath("$.result.contents[0].text").isEqualTo("SUBMITTED");
            assertThatJson(readJson).inPath("$.result.contents[0].uri").isEqualTo("task://" + task.id());
        }
    }

    @Test
    void shouldNotifyTaskStatusOnCreate() throws Exception {
        // The extension's create_task tool is async, so notifications don't route
        // through POST-SSE (ThreadLocal not inherited by ForkJoin threads).
        // This test uses a synchronous tool to verify notification delivery.
        startServer(it -> it.tool(
                        new AbstractToolHandler(
                                ToolDescriptor.builder().name("create-sync").build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest req) {
                                ((DispatchContext) ctx).engine().tasks().create();
                                return ToolResult.text("ok");
                            }
                        })
                .extension(TasksExtension.instance())
                .build());

        try (var client = createTestClient()) {
            var sessionId = initializeWithExtension(client);

            var response = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create-sync","arguments":{}}}
                    """);
            assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
            assertThat(response.headers().firstValue("connection"))
                    .as("POST-SSE response must signal Connection: close so the client does not pool the socket")
                    .hasValue("close");
            assertThat(response.body()).contains("notifications/tasks/status");
            assertThat(response.body()).contains("\"status\":\"working\"");
            assertThat(response.body()).contains("\"taskId\":\"");
            assertThat(response.body()).contains("\"createdAt\":");
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

    private String initializeWithExtension(TestMcpClient client) throws Exception {
        var initBody = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"extensions":{"io.modelcontextprotocol/tasks":{}}},"clientInfo":{"name":"test","version":"1.0"}}}
            """;
        var response = client.post(null, initBody);
        var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
        client.sendInitialized(sessionId);
        return sessionId;
    }

    private static String extractText(String json) {
        try {
            return new ObjectMapper()
                    .readTree(json)
                    .path("result")
                    .path("content")
                    .get(0)
                    .path("text")
                    .asString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract $.result.content[0].text from: " + json, e);
        }
    }
}
