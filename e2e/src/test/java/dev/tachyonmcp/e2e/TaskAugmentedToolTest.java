/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DisabledUntil;

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
            var sessionId = client.initialize();

            var before = System.currentTimeMillis();
            var response = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
                """);
            var elapsed = System.currentTimeMillis() - before;

            assertThat(response).contains("\"task\"");
            assertThat(response).contains("\"status\":\"working\"");
            assertThat(elapsed).isLessThan(sleepMs);
        }
    }

    @Test
    @DisabledUntil(date = "2026-07-18", reason = "fixme")
    void taskAugmentedSyncToolTaskCompletesAfterToolFinishes() throws Exception {
        startServer(it -> it.tool(new SleepingSyncTool(500)));
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var response = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);

            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> {
                        var getJson = rpc(client.httpClient(), port, sessionId, """
                        {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"%s"}}
                        """.formatted(taskId));
                        return getJson.contains("\"completed\"");
                    });

            var resultJson = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(resultJson).inPath("$.result.result").isObject();
            assertThatJson(resultJson).inPath("$.result.result.content[0].text").isEqualTo("done");
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
            var sessionId = client.initialize();

            var response = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                  "name":"task_tool",
                  "arguments":{},
                  "task":{
                    "ttl": 60000
                  }}}
                """);
            var taskId = extractTaskId(response);

            assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            var cancelJson = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tasks/cancel","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(cancelJson).inPath("$.result.status").isEqualTo("cancelled");

            var getJson = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tasks/get","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(getJson).inPath("$.result.status").isEqualTo("cancelled");
        }
    }

    @Test
    @DisabledUntil(date = "2026-07-18", reason = "fixme")
    void taskResultSurfacesErrorMessageWithSpecialChars() throws Exception {
        var handler = ToolHandler.of(b -> b.name("thrower").taskSupport(TaskSupport.OPTIONAL), (context, args) -> {
            throw new RuntimeException("boom \"quoted\"");
        });

        startServer(it -> it.tool(handler));
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var response = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"thrower","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);

            server.tasks().get(taskId).fail(TaskResult.failed("xxx"));

            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> {
                        var getJson = rpc(client.httpClient(), port, sessionId, """
                        {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"%s"}}
                        """.formatted(taskId));
                        return getJson.contains("\"failed\"");
                    });

            var resultJson = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tasks/result","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(resultJson).inPath("$.result.result.error").isStringEqualTo("Internal server error");
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
