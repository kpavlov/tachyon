/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Covers {@link McpOperationHandler}'s POST request-dispatch paths and connection-lifecycle
 * callbacks that the basic handler test does not exercise: JSON-RPC request dispatch, the
 * client→server response/error paths, the POST→SSE upgrade, idle/exception handling, and the
 * stateless GET branch. {@link InteractionHandler} sits in front so {@code handlePostRequest} can
 * resolve the per-channel interaction context, mirroring the production pipeline.
 */
class McpOperationHandlerRequestTest {

    private static final ToolDescriptor PROGRESS_DESCRIPTOR = ToolDescriptor.builder("progress_tool")
            .description("emits progress notifications")
            .inputSchema(
                    JsonNodeFactory.instance.objectNode().put("type", "object").putObject("properties"))
            .build();

    /** Emits two progress notifications (which upgrade the POST response to SSE), then returns a result. */
    private static final ToolHandler PROGRESS_TOOL = new ToolHandler() {
        @Override
        public ToolDescriptor descriptor() {
            return PROGRESS_DESCRIPTOR;
        }

        @Override
        public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
            var pt = request.progressToken();
            ctx.notifications().progress(pt, 0, 100, "Starting");
            ctx.notifications().progress(pt, 100, 100, "Complete");
            return CompletableFuture.completedFuture(ToolResult.text("ok"));
        }
    };

    private Server server;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        server = TachyonServer.builder().tool(PROGRESS_TOOL).build();
        var dispatcher = new McpDispatcher(server, Runnable::run);
        channel = new EmbeddedChannel(
                new InteractionHandler(), new McpOperationHandler(server, dispatcher, Runnable::run));
        server.createSession("sess-op").activate();
    }

    @AfterEach
    void tearDown() {
        channel.close();
        server.close();
    }

    // A successful JSON-RPC request returns its result as a single JSON response (keep-alive path).
    @Test
    void postRequestReturnsJsonResult() {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
        assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat(response.headers().get(HttpHeaderNames.CONTENT_TYPE)).contains("application/json");
        assertThat(response.content().toString(StandardCharsets.UTF_8)).contains("result");
        response.release();
    }

    // An unknown method dispatches to a JSON-RPC error envelope, still over a 200 JSON response.
    @Test
    void postUnknownMethodReturnsJsonRpcError() {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"does/notExist\"}");
        assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);
        var body = response.content().toString(StandardCharsets.UTF_8);
        assertThat(body).contains("error").contains("Method not found");
        response.release();
    }

    // A client→server JSON-RPC response (no pending request) is acknowledged with 202.
    @Test
    void postResponseMessageReturnsAccepted() {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":\"42\",\"result\":{}}");
        assertThat(response.status()).isEqualTo(HttpResponseStatus.ACCEPTED);
        response.release();
    }

    // A client→server JSON-RPC error (no pending request) is acknowledged with 202.
    @Test
    void postErrorMessageReturnsAccepted() {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":\"42\",\"error\":{\"code\":-1,\"message\":\"boom\"}}");
        assertThat(response.status()).isEqualTo(HttpResponseStatus.ACCEPTED);
        response.release();
    }

    // A non-"initialized" notification is acknowledged with 202 and dispatched asynchronously.
    @Test
    void postCancelledNotificationReturnsAccepted() {
        var response = post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
                + "\"params\":{\"requestId\":\"1\",\"reason\":\"user\"}}");
        assertThat(response.status()).isEqualTo(HttpResponseStatus.ACCEPTED);
        response.release();
    }

    // A tool that emits progress upgrades the POST response to an SSE stream; the final result is
    // delivered as the last SSE event (SEP-1699 POST-SSE streaming).
    @Test
    void toolProgressUpgradesPostResponseToSseStream() {
        writeInbound("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"progress_tool\",\"arguments\":{},\"_meta\":{\"progressToken\":42}}}");

        var outbound = drainOutbound();
        try {
            var head = outbound.getFirst();
            assertThat(head).isInstanceOf(HttpResponse.class).isNotInstanceOf(FullHttpResponse.class);
            var sseResponse = (HttpResponse) head;
            assertThat(sseResponse.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(sseResponse.headers().get(HttpHeaderNames.CONTENT_TYPE)).isEqualTo("text/event-stream");

            var streamBody = contentText(outbound);
            assertThat(streamBody)
                    .contains("notifications/progress")
                    .contains("\"progress\":0.0")
                    .contains("\"progress\":100.0")
                    .contains("result")
                    .contains("ok");
        } finally {
            outbound.forEach(ReferenceCountUtil::release);
        }
    }

    // Once a tool upgrades the POST response to SSE, the stream is long-lived: an idle tick must
    // emit a comment heartbeat (":\r\n") to keep it alive instead of closing the channel.
    @Test
    void idleOnUpgradedPostSseStreamSendsHeartbeat() throws InterruptedException {
        var descriptor = ToolDescriptor.builder("stalled_tool")
                .description("upgrades to SSE then never completes")
                .inputSchema(JsonNodeFactory.instance
                        .objectNode()
                        .put("type", "object")
                        .putObject("properties"))
                .build();
        var upgraded = new CountDownLatch(1);
        var neverComplete = new CompletableFuture<ToolResult>();
        ToolHandler stalledTool = new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                ctx.notifications().progress(request.progressToken(), 0, 100, "working");
                upgraded.countDown();
                return neverComplete;
            }
        };
        var srv = TachyonServer.builder().tool(stalledTool).build();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        var ch = new EmbeddedChannel(
                new InteractionHandler(),
                new McpOperationHandler(srv, new McpDispatcher(srv, pool::execute), Runnable::run));
        srv.createSession("sess-stall").activate();
        try {
            var request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/mcp",
                    Unpooled.copiedBuffer(
                            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{"
                                    + "\"name\":\"stalled_tool\",\"arguments\":{},\"_meta\":{\"progressToken\":7}}}",
                            StandardCharsets.UTF_8));
            request.headers()
                    .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                    .set("MCP-Session-Id", "sess-stall")
                    .set(HttpHeaderNames.ACCEPT, "application/json, text/event-stream");
            ch.writeInbound(request);
            assertThat(upgraded.await(5, TimeUnit.SECONDS))
                    .as("tool must emit progress within 5 s")
                    .isTrue();
            drain(ch).forEach(ReferenceCountUtil::release);

            ch.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
            ch.runPendingTasks();

            assertThat(ch.isOpen())
                    .as("upgraded POST-SSE stream must survive an idle tick")
                    .isTrue();
            var heartbeat = ch.readOutbound();
            assertThat(heartbeat).isInstanceOf(HttpContent.class);
            var content = (HttpContent) heartbeat;
            try {
                assertThat(content.content().toString(StandardCharsets.UTF_8)).isEqualTo(":\r\n");
            } finally {
                content.release();
            }
        } finally {
            neverComplete.cancel(true);
            ch.close();
            srv.close();
            pool.shutdownNow();
        }
    }

    // GET with a session id that does not exist is rejected with 400.
    @Test
    void getUnknownSessionReturnsBadRequest() {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set(HttpHeaderNames.ACCEPT, "text/event-stream")
                .set("MCP-Session-Id", "ghost");
        channel.writeInbound(request);

        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        assertThat(response.content().toString(StandardCharsets.UTF_8)).contains("Unknown session");
        response.release();
    }

    // An idle-timeout event closes the channel.
    @Test
    void idleTimeoutClosesChannel() {
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        channel.runPendingTasks();
        assertThat(channel.isOpen()).isFalse();
    }

    // A connection reset (SocketException) closes the channel quietly.
    @Test
    void socketExceptionClosesChannel() {
        channel.pipeline().fireExceptionCaught(new SocketException("connection reset"));
        channel.runPendingTasks();
        assertThat(channel.isOpen()).isFalse();
    }

    // Any other exception closes the channel.
    @Test
    void genericExceptionClosesChannel() {
        channel.pipeline().fireExceptionCaught(new RuntimeException("boom"));
        channel.runPendingTasks();
        assertThat(channel.isOpen()).isFalse();
    }

    // In stateless mode a GET opens an SSE stream without requiring a session.
    @Test
    void statelessGetOpensSseStream() {
        var statelessServer =
                TachyonServer.builder().session(s -> s.stateless(true)).build();
        var ch = new EmbeddedChannel(
                new InteractionHandler(),
                new McpOperationHandler(
                        statelessServer, new McpDispatcher(statelessServer, Runnable::run), Runnable::run));
        try {
            var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp");
            request.headers()
                    .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                    .set(HttpHeaderNames.ACCEPT, "text/event-stream");
            ch.writeInbound(request);

            var outbound = drain(ch);
            try {
                assertThat(outbound.getFirst()).isInstanceOf(HttpResponse.class);
                var response = (HttpResponse) outbound.getFirst();
                assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);
                assertThat(response.headers().get(HttpHeaderNames.CONTENT_TYPE)).isEqualTo("text/event-stream");
            } finally {
                outbound.forEach(ReferenceCountUtil::release);
            }
        } finally {
            ch.close();
            statelessServer.close();
        }
    }

    private FullHttpResponse post(String body) {
        writeInbound(body);
        return readResponse();
    }

    private void writeInbound(String body) {
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Session-Id", "sess-op")
                .set(HttpHeaderNames.ACCEPT, "application/json, text/event-stream");
        channel.writeInbound(request);
    }

    private FullHttpResponse readResponse() {
        channel.runPendingTasks();
        var msg = channel.readOutbound();
        assertThat(msg).isInstanceOf(FullHttpResponse.class);
        return (FullHttpResponse) msg;
    }

    private List<Object> drainOutbound() {
        return drain(channel);
    }

    private static List<Object> drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        var messages = new ArrayList<>();
        Object msg;
        while ((msg = ch.readOutbound()) != null) {
            messages.add(msg);
        }
        assertThat(messages).as("expected at least one outbound message").isNotEmpty();
        return messages;
    }

    private static String contentText(List<Object> messages) {
        var sb = new StringBuilder();
        for (var msg : messages) {
            if (msg instanceof HttpContent content) {
                sb.append(content.content().toString(StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }
}
