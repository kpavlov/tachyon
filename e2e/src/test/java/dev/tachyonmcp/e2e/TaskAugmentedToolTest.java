/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.features.tools.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    void cancelTaskInterruptsRunningSyncHandler() throws Exception {
        var interrupted = new AtomicBoolean(false);
        var latch = new CountDownLatch(1);

        var handler = new SyncToolHandler() {
            private final ToolDescriptor d = ToolDescriptor.builder()
                    .name("blocking")
                    .taskSupport(TaskSupport.OPTIONAL)
                    .build();

            @Override
            public ToolDescriptor descriptor() {
                return d;
            }

            @Override
            public String name() {
                return "blocking";
            }

            @Override
            public ToolResult handle(InteractionContext context, ToolArgs arguments) {
                try {
                    latch.countDown();
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
                return ToolResult.text("done");
            }
        };

        startServer(it -> it.tool(handler));
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var response = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"blocking","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            var cancelJson = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tasks/cancel","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(cancelJson).inPath("$.result.status").isEqualTo("cancelled");

            await().atMost(Duration.ofSeconds(3))
                    .pollInterval(Duration.ofMillis(50))
                    .untilTrue(interrupted);

            var getJson = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tasks/get","params":{"taskId":"%s"}}
                """.formatted(taskId));
            assertThatJson(getJson).inPath("$.result.status").isEqualTo("cancelled");
        }
    }

    @Test
    void taskResultSurfacesErrorMessageWithSpecialChars() throws Exception {
        var handler = new SyncToolHandler() {
            private final ToolDescriptor d = ToolDescriptor.builder()
                    .name("thrower")
                    .taskSupport(TaskSupport.OPTIONAL)
                    .build();

            @Override
            public ToolDescriptor descriptor() {
                return d;
            }

            @Override
            public String name() {
                return "thrower";
            }

            @Override
            public ToolResult handle(InteractionContext context, ToolArgs arguments) {
                throw new RuntimeException("boom \"quoted\"");
            }
        };

        startServer(it -> it.tool(handler));
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var response = rpc(client.httpClient(), port, sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"thrower","arguments":{},"task":{}}}
                """);
            var taskId = extractTaskId(response);

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

    private record SleepingSyncTool(int sleepMs) implements ToolHandler {
        private static final ToolDescriptor D = ToolDescriptor.builder()
                .name("sleep")
                .taskSupport(TaskSupport.OPTIONAL)
                .build();

        @Override
        public ToolDescriptor descriptor() {
            return D;
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolRequest request) throws Exception {
            Thread.sleep(sleepMs);
            return ToolResult.text("done");
        }
    }
}
