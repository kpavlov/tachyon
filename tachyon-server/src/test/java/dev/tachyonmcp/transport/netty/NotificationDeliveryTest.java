/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.server.session.McpSession;
import dev.tachyonmcp.server.session.SseConnection;
import dev.tachyonmcp.server.session.SseEvent;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class NotificationDeliveryTest {

    private static final ToolDescriptor TOOL_DESCRIPTOR = ToolDescriptor.builder("test_tool")
            .description("Test tool")
            .inputSchema(
                    JsonNodeFactory.instance.objectNode().put("type", "object").putObject("properties"))
            .build();

    /** Emits 3 progress events and 3 log events per invocation (plus 2 automatic lifecycle logs from ToolsCallHandler). */
    private static final ToolHandler PROGRESS_AND_LOG_TOOL = new ToolHandler() {
        @Override
        public ToolDescriptor descriptor() {
            return TOOL_DESCRIPTOR;
        }

        @Override
        public CompletionStage<ToolResult> handle(ToolRequest request, McpContext ctx) {
            var pt = request.progressToken();
            ctx.notifications().progress(pt, 0, 100, "Starting");
            ctx.notifications().progress(pt, 50, 100, "Halfway");
            ctx.notifications().progress(pt, 100, 100, "Complete");
            var logData = Map.of("tool", request.name(), "message", "tool log");
            ctx.notifications().info("tachyon.tools", logData);
            ctx.notifications().info("tachyon.tools", logData);
            ctx.notifications().info("tachyon.tools", logData);
            return CompletableFuture.completedFuture(ToolResult.text("ok"));
        }
    };

    private McpServer server;
    private McpDispatcher dispatcher;
    private CollectingConnection testConn;

    @BeforeEach
    void setUp() {
        server = TachyonServer.builder().tool(PROGRESS_AND_LOG_TOOL).build();
        dispatcher = new McpDispatcher(server, server.executor());
        testConn = new CollectingConnection();
        McpSession session = server.createSession("sess_test");
        session.connection(testConn);
        session.activate();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void shouldSendProgressNotification() {
        var params = java.util.Map.of(
                "name", "test_tool",
                "arguments", java.util.Map.of(),
                "_meta", java.util.Map.of("progressToken", 42));
        var result = dispatcher
                .dispatchRequestAsync(1, "tools/call", params, "sess_test")
                .join();

        assertThat(result).isInstanceOf(McpDispatcher.DispatchResult.Response.class);
        assertThat(((McpDispatcher.DispatchResult.Response) result).responseBody())
                .isNotNull();

        var progressEvents = testConn.sent.stream()
                .filter(e -> e.data().contains("notifications/progress"))
                .toList();
        assertThat(progressEvents).hasSize(3);
        assertThat(progressEvents.get(0).data()).contains("\"progress\":0.0");
        assertThat(progressEvents.get(0).data()).contains("\"progressToken\":42");
        assertThat(progressEvents.get(1).data()).contains("\"progress\":50.0");
        assertThat(progressEvents.get(2).data()).contains("\"progress\":100.0");
    }

    @Test
    void shouldSendLoggingNotificationWhenLevelIsSet() {
        var levelParams = java.util.Map.of("level", "info");
        dispatcher
                .dispatchRequestAsync(1, "logging/setLevel", levelParams, "sess_test")
                .join();

        var toolParams = java.util.Map.of(
                "name", "test_tool",
                "arguments", java.util.Map.of(),
                "_meta", java.util.Map.of("progressToken", 42));
        dispatcher
                .dispatchRequestAsync(2, "tools/call", toolParams, "sess_test")
                .join();

        // 3 from askable + 2 from automatic lifecycle logging (started/completed)
        var logEvents = testConn.sent.stream()
                .filter(e -> e.data().contains("notifications/message"))
                .toList();
        assertThat(logEvents).hasSize(5);
        assertThat(logEvents.getFirst().data()).contains("\"level\":\"info\"");
        assertThat(logEvents.getFirst().data()).contains("\"logger\":\"tachyon.tools\"");
    }

    @Test
    void shouldNotSendProgressWithoutMeta() {
        var params = java.util.Map.of("name", "test_tool", "arguments", java.util.Map.of());
        dispatcher.dispatchRequestAsync(1, "tools/call", params, "sess_test").join();

        var progressEvents = testConn.sent.stream()
                .filter(e -> e.data().contains("notifications/progress"))
                .toList();
        assertThat(progressEvents).isEmpty();
    }

    @Test
    void shouldSendLoggingWithoutLevelSet() {
        var params = java.util.Map.of(
                "name", "test_tool",
                "arguments", java.util.Map.of(),
                "_meta", java.util.Map.of("progressToken", 42));
        dispatcher.dispatchRequestAsync(1, "tools/call", params, "sess_test").join();

        // info() sends unconditionally; sendLoggingIfEnabled skips (no level configured)
        var logEvents = testConn.sent.stream()
                .filter(e -> e.data().contains("notifications/message"))
                .toList();
        assertThat(logEvents).hasSize(3);
        assertThat(logEvents).allSatisfy(e -> {
            assertThat(e.data()).contains("\"level\":\"info\"");
            assertThat(e.data()).contains("\"logger\":\"tachyon.tools\"");
        });
    }

    @Test
    void shouldHandleMultipleToolsSequentially() {
        dispatcher
                .dispatchRequestAsync(1, "logging/setLevel", java.util.Map.of("level", "debug"), "sess_test")
                .join();

        for (int i = 0; i < 3; i++) {
            var params = java.util.Map.of(
                    "name", "test_tool",
                    "arguments", java.util.Map.of(),
                    "_meta", java.util.Map.of("progressToken", i));
            dispatcher
                    .dispatchRequestAsync(2 + i, "tools/call", params, "sess_test")
                    .join();
        }

        for (int t = 0; t < 3; t++) {
            var token = t;
            var tokenEvents = testConn.sent.stream()
                    .filter(e -> e.data().contains("\"progressToken\":" + token))
                    .toList();
            assertThat(tokenEvents).as("progress events for token " + token).hasSize(3);
            assertThat(tokenEvents.get(0).data()).contains("\"progress\":0.0");
            assertThat(tokenEvents.get(1).data()).contains("\"progress\":50.0");
            assertThat(tokenEvents.get(2).data()).contains("\"progress\":100.0");
        }

        // 3 tools × (3 from askable + 2 from automatic lifecycle logging)
        var logEvents = testConn.sent.stream()
                .filter(e -> e.data().contains("notifications/message"))
                .toList();
        assertThat(logEvents).hasSize(15);
        assertThat(logEvents).allSatisfy(e -> {
            assertThat(e.data()).containsAnyOf("\"level\":\"info\"", "\"level\":\"debug\"");
            assertThat(e.data()).contains("\"logger\":\"tachyon.tools\"");
            assertThat(e.data()).contains("\"tool\":\"test_tool\"");
        });
    }

    @Test
    void shouldSendEventOnlyToCorrectSession() {
        var conn2 = new CollectingConnection();
        var session2 = server.createSession("sess_other");
        session2.connection(conn2);
        session2.activate();

        dispatcher
                .dispatchRequestAsync(1, "logging/setLevel", java.util.Map.of("level", "info"), "sess_test")
                .join();
        dispatcher
                .dispatchRequestAsync(2, "logging/setLevel", java.util.Map.of("level", "warning"), "sess_other")
                .join();

        var params = java.util.Map.of(
                "name", "test_tool",
                "arguments", java.util.Map.of(),
                "_meta", java.util.Map.of("progressToken", 1));
        dispatcher.dispatchRequestAsync(3, "tools/call", params, "sess_test").join();

        // 3 from askable + 2 from automatic lifecycle logging
        var testLogEvents = testConn.sent.stream()
                .filter(e -> e.data().contains("notifications/message"))
                .toList();
        assertThat(testLogEvents).hasSize(5);
        assertThat(testLogEvents.getFirst().data()).contains("\"level\":\"info\"");

        var otherLogEvents = conn2.sent.stream()
                .filter(e -> e.data().contains("notifications/message"))
                .toList();
        assertThat(otherLogEvents).isEmpty();
    }

    private static class CollectingConnection implements SseConnection {

        final ArrayList<SseEvent> sent = new ArrayList<>();
        final boolean writable = true;

        @Override
        public boolean isWritable() {
            return writable;
        }

        @Override
        public void send(SseEvent event) {
            sent.add(event);
        }
    }
}
