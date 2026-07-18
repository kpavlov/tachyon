/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.junit.jupiter.api.Test;

class TaskAugmentedToolTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> {});
    }

    @Test
    void taskAugmentedCallReturnsCreateTaskResultBeforeToolCompletes() throws Exception {
        var sleepMs = 2000;
        startServer(it -> it.tool(new SleepingSyncTool(sleepMs)));
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
        startServer(it -> it.tool(new SleepingSyncTool(500)));
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);

            client.awaitTaskStatus(taskId, "completed");

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
    void shouldCancelTask() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var handler = ToolHandler.of(b -> b.name("task_tool").taskSupport(TaskSupport.OPTIONAL), (context, args) -> {
            started.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.text("done");
        });

        startServer(it -> it.tool(handler));
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
        var handler = ToolHandler.of(b -> b.name("sleep").taskSupport(TaskSupport.OPTIONAL), (context, args) -> {
            release.await();
            return ToolResult.text("done");
        });
        startServer(it -> it.tool(handler));
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);

            server.tasks().get(taskId).fail(TaskResult.failed("boom \"quoted\""));

            client.awaitTaskStatus(taskId, "failed");

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
}
