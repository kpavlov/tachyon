/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.features.tasks.TasksExtension;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TasksExtensionE2eTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(TachyonMcpServer.builder().extension(new TasksExtension()).build());
    }

    @Test
    void toolAndTemplateVisibleWhenExtensionNegotiated() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = initializeWithExtension(client);

            var toolsJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                    """);
            assertThatJson(toolsJson)
                    .inPath("$.result.tools[?(@.name=='create_task')]")
                    .isArray()
                    .isNotEmpty();
            assertThatJson(toolsJson)
                    .inPath("$.result.tools[?(@.name=='create_task')].description")
                    .isArray()
                    .isNotEmpty();
            assertThatJson(toolsJson)
                    .inPath("$.result.tools[?(@.name=='create_task')].inputSchema")
                    .isArray()
                    .isNotEmpty();

            var templatesJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":3,"method":"resources/templates/list","params":{}}
                    """);
            assertThatJson(templatesJson)
                    .inPath("$.result.resourceTemplates[?(@.uriTemplate=='task://{id}')]")
                    .isArray()
                    .isNotEmpty();
            assertThatJson(templatesJson)
                    .inPath("$.result.resourceTemplates[?(@.uriTemplate=='task://{id}')].name")
                    .isArray()
                    .isNotEmpty();
            assertThatJson(templatesJson)
                    .inPath("$.result.resourceTemplates[?(@.uriTemplate=='task://{id}')].description")
                    .isArray()
                    .isNotEmpty();
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

            server.tasks().completeTask(taskId, "{\"output\":\"completed\"}");

            var getAfterJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":4,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(taskId));
            assertThatJson(getAfterJson).inPath("$.result.taskId").isEqualTo(taskId);
            assertThatJson(getAfterJson).inPath("$.result.status").isEqualTo("completed");

            var resultJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":5,"method":"tasks/result","params":{"taskId":"%s"}}
                    """.formatted(taskId));
            assertThatJson(resultJson).inPath("$.result.result").isObject().containsEntry("output", "completed");
        }
    }

    @Test
    void readTaskResourceTemplate() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = initializeWithExtension(client);

            var task = server.tasks().createTask("res-task", "task for resource read");

            var readJson = rpc(client.httpClient(), port, sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"task://%s"}}
                    """.formatted(task.id()));
            assertThatJson(readJson).inPath("$.result.contents[0].text").isEqualTo("WORKING");
            assertThatJson(readJson).inPath("$.result.contents[0].uri").isEqualTo("task://" + task.id());
        }
    }

    @Test
    void shouldNotifyTaskStatusOnCreate() throws Exception {
        // The extension's create_task tool is async, so notifications don't route
        // through POST-SSE (ScopedValue not inherited by ForkJoin threads).
        // This test uses a synchronous tool to verify notification delivery.
        startServer(TachyonMcpServer.builder()
                .tool(new ToolHandler() {
                    private final ToolDescriptor d =
                            ToolDescriptor.builder("create-sync").build();

                    @Override
                    public ToolDescriptor descriptor() {
                        return d;
                    }

                    @Override
                    public CompletionStage<ToolResult> handle(ToolRequest req, McpContext ctx) {
                        ctx.server().mcpServer().tasks().createTask("sync-notif-task", null);
                        return CompletableFuture.completedFuture(ToolResult.text("ok"));
                    }
                })
                .extension(new TasksExtension())
                .build());

        try (var client = createTestClient()) {
            var sessionId = initializeWithExtension(client);

            var response = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create-sync","arguments":{}}}
                    """);
            assertThat(response.body()).contains("notifications/tasks/status");
            assertThat(response.body()).contains("\"status\":\"working\"");
            assertThat(response.body()).contains("\"taskId\":\"");
            assertThat(response.body()).contains("\"createdAt\":");
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
                    .asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract $.result.content[0].text from: " + json, e);
        }
    }
}
