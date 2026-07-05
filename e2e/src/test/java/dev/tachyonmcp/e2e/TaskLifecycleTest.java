/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class TaskLifecycleTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(new EchoToolHandler()));
    }

    @Test
    void shouldListTasksWhenNoneRegistered() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            // language=JSON
            var listBody = """
                {"jsonrpc":"2.0","id":2,"method":"tasks/list","params":{}}
                """;
            var responseJson = rpc(client.httpClient(), port, sessionId, listBody);
            assertThatJson(responseJson).inPath("$.result.tasks").isArray();
        }
    }

    @Test
    void shouldCreateListAndGetTask() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var task = server.tasks().createTask("test-task", "A test task");

            var listBody = """
                {"jsonrpc":"2.0","id":2,"method":"tasks/list","params":{}}
                """;
            var listResponse = rpc(client.httpClient(), port, sessionId, listBody);
            assertThatJson(listResponse).inPath("$.result.tasks.length()").isEqualTo(1);

            var getBody = """
                {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"%s"}}
                """.formatted(task.id());
            var getResponse = rpc(client.httpClient(), port, sessionId, getBody);
            assertThatJson(getResponse).inPath("$.result.taskId").isEqualTo(task.id());
            assertThatJson(getResponse).inPath("$.result.status").isEqualTo("working");
        }
    }

    @Test
    void shouldCancelTask() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var task = server.tasks().createTask("cancel-task", "A task to cancel");

            var cancelBody = """
                {"jsonrpc":"2.0","id":2,"method":"tasks/cancel","params":{"taskId":"%s"}}
                """.formatted(task.id());
            var cancelResponse = rpc(client.httpClient(), port, sessionId, cancelBody);
            assertThatJson(cancelResponse).inPath("$.result.status").isEqualTo("cancelled");
            assertThatJson(cancelResponse).inPath("$.result.taskId").isEqualTo(task.id());
        }
    }

    @Test
    void shouldCompleteTaskAndGetResult() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var task = server.tasks().createTask("result-task", "A task with result");
            server.tasks().completeTask(task.id(), "{\"output\":\"success\"}");

            var getBody = """
                {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"%s"}}
                """.formatted(task.id());
            var getResponse = rpc(client.httpClient(), port, sessionId, getBody);
            assertThatJson(getResponse).inPath("$.result.status").isEqualTo("completed");

            var resultBody = """
                {"jsonrpc":"2.0","id":3,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(task.id());
            var resultResponse = rpc(client.httpClient(), port, sessionId, resultBody);
            assertThatJson(resultResponse).inPath("$.result").isObject();
        }
    }

    @Test
    void shouldReturnErrorForTaskResultOnWorkingTask() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var task = server.tasks().createTask("pending-task", "Still running");

            var resultBody = """
                {"jsonrpc":"2.0","id":2,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(task.id());
            var resultResponse = rpc(client.httpClient(), port, sessionId, resultBody);
            assertThatJson(resultResponse).inPath("$.error.code").isNumber();
            assertThatJson(resultResponse).inPath("$.error.message").isString();
        }
    }

    @Test
    void shouldReturnErrorForUnknownTask() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var getBody = """
                {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"nonexistent-id"}}
                """;
            var getResponse = rpc(client.httpClient(), port, sessionId, getBody);
            assertThatJson(getResponse).inPath("$.error.code").isNumber();
        }
    }

    @Test
    void shouldNotifyTaskStatusViaSyncTool() throws Exception {
        startServer(it -> it.tool(new EchoToolHandler()).tool(new SyncTaskCreatorTool()));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var response = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create-task-sync","arguments":{"name":"notif-task"}}}
                    """);
            assertThat(response.body()).contains("notifications/tasks/status");
            assertThat(response.body()).contains("\"status\":\"working\"");
        }
    }

    private class SyncTaskCreatorTool implements ToolHandler {
        private final ToolDescriptor descriptor = ToolDescriptor.builder()
                .name("create-task-sync")
                .description("Creates a task synchronously")
                .build();

        @Override
        public ToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public CompletionStage<? extends ToolResult> handle(InteractionContext context, ToolRequest request) {
            var args = ToolArgs.of(request.arguments());
            var name = args.stringOr("name", "unnamed");
            server.tasks().createTask(name, null);
            return CompletableFuture.completedFuture(ToolResult.text("created"));
        }
    }
}
