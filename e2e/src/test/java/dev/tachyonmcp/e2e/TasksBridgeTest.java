/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ClientCapabilities;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Implementation;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskState;
import dev.tachyonmcp.server.features.tasks.TasksExtension;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class TasksBridgeTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(TachyonMcpServer.builder().capabilities(c -> c.tasks()).build());
    }

    @Test
    void tasksWorkAsCoreFor2025_11_25Client() throws Exception {
        startDefaultServer();

        try (var client = createTestClient()) {
            // Initialize without mentioning the tasks extension
            var initBody = buildInitializeJson(Map.of());
            var response = client.post(null, initBody);
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.sendInitialized(sessionId);

            // capabilities.tasks should be advertised
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.tasks")
                    .isObject();

            // tasks/list works
            var listResp = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tasks/list"}
                    """);
            assertThatJson(listResp.body()).inPath("$.result.tasks").isArray();
        }
    }

    @Test
    void tasksExtensionAdvertisedWhenNegotiated() throws Exception {
        startServer(TachyonMcpServer.builder()
                .capabilities(c -> c.tasks())
                .extension(new TasksExtension())
                .build());

        try (var client = createTestClient()) {
            var initBody =
                    buildInitializeJson(Map.of("io.modelcontextprotocol/tasks", JsonNodeFactory.instance.objectNode()));
            var response = client.post(null, initBody);
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.sendInitialized(sessionId);

            // Both capabilities.tasks and capabilities.extensions should be present
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.tasks")
                    .isObject();
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.extensions")
                    .isObject()
                    .containsKey("io.modelcontextprotocol/tasks");

            // tasks/list works
            var listResp = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tasks/list"}
                    """);
            assertThatJson(listResp.body()).inPath("$.result.tasks").isArray();
        }
    }

    @Test
    void singleRegistryBacksBothPaths() throws Exception {
        startServer(TachyonMcpServer.builder().extension(new TasksExtension()).build());

        // Add a task directly to the registry
        var taskEntry =
                new TaskEntry(TaskDescriptor.of("test-task", "A test task"), "test-task-1", TaskState.WORKING, 60.0);
        server.tasks().add(taskEntry);

        try (var client = createTestClient()) {
            var initBody =
                    buildInitializeJson(Map.of("io.modelcontextprotocol/tasks", JsonNodeFactory.instance.objectNode()));
            var response = client.post(null, initBody);
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.sendInitialized(sessionId);

            // tasks/get works through core path to see the task
            var getResp = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"test-task-1"}}
                    """);
            assertThatJson(getResp.body()).inPath("$.result.taskId").isEqualTo("test-task-1");
        }
    }

    private static String buildInitializeJson(Map<String, tools.jackson.databind.JsonNode> extensions) {
        var capsBuilder = ClientCapabilities.builder();
        if (!extensions.isEmpty()) {
            capsBuilder.extensions(extensions);
        }
        var caps = capsBuilder.build();
        var params = InitializeRequestParams.builder()
                .protocolVersion("2025-11-25")
                .capabilities(caps)
                .clientInfo(new Implementation("1.0", null, null, "test-client", null, null))
                .build();
        var mapper = new tools.jackson.databind.ObjectMapper();
        try {
            var paramsJson = mapper.writeValueAsString(params);
            return """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":%s}
                    """.formatted(paramsJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
