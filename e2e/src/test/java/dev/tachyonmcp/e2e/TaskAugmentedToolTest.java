/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.PayloadSerializer;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;
import dev.tachyonmcp.api.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;

class TaskAugmentedToolTest extends AbstractStatelessMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> {});
    }

    @Test
    void taskAugmentedCallReturnsCreateTaskResultBeforeToolCompletes() throws Exception {
        var sleepMs = 2000;
        var tool = new SleepingSyncTool(sleepMs);
        startServerWith(s -> s.tools().register(tool.descriptor(), tool::handle));
        try (var client = createTestClient()) {
            client.initialize();

            var before = System.currentTimeMillis();
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
                """);
            var elapsed = System.currentTimeMillis() - before;

            assertThat(response).contains("\"task\"");
            assertThat(response).contains("\"status\":\"working\"");
            assertThat(elapsed).isLessThan(sleepMs);
        }
    }

    @Test
    void taskAugmentedSyncToolTaskCompletesAfterToolFinishes() throws Exception {
        var tool = new SleepingSyncTool(500);
        startServerWith(s -> s.tools().register(tool.descriptor(), tool::handle));
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
            """);
            var taskId = extractTaskId(response);

            var completedTask = client.awaitTaskStatus(taskId, "completed");
            // language=JSON
            var expectedTask = """
                    {
                      "jsonrpc": "2.0",
                      "id": "tasks-get",
                      "result": {
                        "taskId": "%s",
                        "status": "completed"
                      }
                    }
                    """.formatted(taskId);
            assertThatJson(completedTask).when(Option.IGNORING_EXTRA_FIELDS).isEqualTo(expectedTask);

            var resultJson = client.sendRpc("""
                {"jsonrpc":"2.0","id":4,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(taskId));
            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 4,
                      "result": {
                        "content": [
                          {"type": "text", "text": "done"}
                        ],
                        "_meta": {
                          "io.modelcontextprotocol/related-task": {"taskId": "%s"}
                        }
                      }
                    }
                    """.formatted(taskId);
            assertThatJson(resultJson).isEqualTo(expected);
        }
    }

    @Test
    void taskResultUsesConfiguredPayloadSerializerAndMetadata() throws Exception {
        startServer(
                b -> b.json(j -> j.serializer(new PayloadSerializer() {
                    @Override
                    public <T> String serialize(T value) {
                        var payload = (CustomPayload) value;
                        return "{\"serializedBy\":\"custom\",\"value\":\"" + payload.value() + "\"}";
                    }
                })),
                s -> s.tools()
                        .register(
                                b -> b.name("custom-payload").taskSupport(TaskSupport.OPTIONAL),
                                (context, request) ->
                                        ToolResult.of(new CustomPayload("task")).withMeta("result-meta", "kept")));
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                  "name":"custom-payload","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);
            client.awaitTaskStatus(taskId, "completed");

            var resultJson = client.sendRpc("""
                {"jsonrpc":"2.0","id":4,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(resultJson).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 4,
                      "result": {
                        "content": [{
                          "type": "text",
                          "text": "{\\"serializedBy\\":\\"custom\\",\\"value\\":\\"task\\"}"
                        }],
                        "structuredContent": {
                          "serializedBy": "custom",
                          "value": "task"
                        },
                        "_meta": {
                          "result-meta": "kept",
                          "io.modelcontextprotocol/related-task": {"taskId": "%s"}
                        }
                      }
                    }
                    """.formatted(taskId));
        }
    }

    @Test
    void shouldCancelTask() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        startServerWith(s -> s.tools()
                .register(b -> b.name("task_tool").taskSupport(TaskSupport.OPTIONAL), (context, request) -> {
                    started.countDown();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                    return ToolResult.text("done");
                }));
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                  "name":"task_tool",
                  "arguments":{},
                  "task":{
                    "ttl": 60000
                  }}}
                """);
            var taskId = extractTaskId(response);

            assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            var cancelJson = client.sendRpc("""
                {"jsonrpc":"2.0","id":3,"method":"tasks/cancel","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(cancelJson).inPath("$.result.status").isEqualTo("cancelled");
            assertThat(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();

            var getJson = client.sendRpc("""
                {"jsonrpc":"2.0","id":4,"method":"tasks/get","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(getJson).inPath("$.result.status").isEqualTo("cancelled");
        }
    }

    @Test
    void taskResultSurfacesErrorMessageWithSpecialChars() throws Exception {
        // Blocks the tool indefinitely so the manual fail() below — not the tool itself —
        // deterministically decides the outcome; released only after that outcome is asserted.
        var release = new java.util.concurrent.CountDownLatch(1);
        startServerWith(s -> s.tools()
                .register(b -> b.name("sleep").taskSupport(TaskSupport.OPTIONAL), (context, request) -> {
                    release.await();
                    return ToolResult.text("done");
                }));
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);

            server.tasks().get(taskId).fail(TaskResult.failed("boom \"quoted\""));

            var failedTask = client.awaitTaskStatus(taskId, "failed");
            // language=JSON
            var expectedTask = """
                    {
                      "jsonrpc": "2.0",
                      "id": "tasks-get",
                      "result": {
                        "taskId": "%s",
                        "status": "failed"
                      }
                    }
                    """.formatted(taskId);
            assertThatJson(failedTask).when(Option.IGNORING_EXTRA_FIELDS).isEqualTo(expectedTask);

            var resultJson = client.sendRpc("""
                {"jsonrpc":"2.0","id":4,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(taskId));
            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 4,
                      "result": {
                        "content": [
                          {"type": "text", "text": "boom \\"quoted\\""}
                        ],
                        "isError": true,
                        "_meta": {
                          "io.modelcontextprotocol/related-task": {"taskId": "%s"}
                        }
                      }
                    }
                    """.formatted(taskId);
            assertThatJson(resultJson).isEqualTo(expected);

            release.countDown();
        }
    }

    @Test
    void taskResultReplaysInvalidParamsWithoutLeakingHandlerMessage() throws Exception {
        // MCP 2025-11-25 Tasks: tasks/result MUST return the underlying JSON-RPC error.
        startServerWith(s -> s.tools()
                .register(b -> b.name("invalid-params").taskSupport(TaskSupport.OPTIONAL), (context, request) -> {
                    throw new IllegalArgumentException("sensitive internal detail");
                }));
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                  "name":"invalid-params","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);
            client.awaitTaskStatus(taskId, "failed");

            var resultJson = client.sendRpc("""
                {"jsonrpc":"2.0","id":4,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(taskId));
            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 4,
                      "error": {
                        "code": -32602,
                        "message": "Invalid params"
                      }
                    }
                    """;
            assertThatJson(resultJson).isEqualTo(expected);
            assertThat(resultJson).doesNotContain("sensitive internal detail");
        }
    }

    private static String extractTaskId(String json) {
        try {
            var mapper = new tools.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            return node.get("result").get("task").get("taskId").asString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract taskId from: " + json, e);
        }
    }

    private static final class SleepingSyncTool extends AbstractToolHandler {
        private final int sleepMs;

        SleepingSyncTool(int sleepMs) {
            super(ToolDescriptor.builder()
                    .name("sleep")
                    .taskSupport(TaskSupport.OPTIONAL)
                    .build());
            this.sleepMs = sleepMs;
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolRequest request) throws Exception {
            Thread.sleep(sleepMs);
            return ToolResult.text("done");
        }
    }

    private record CustomPayload(String value) {}
}
