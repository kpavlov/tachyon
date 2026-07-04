/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpOperationHandlerTest {

    private Server server;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        server = TachyonServer.builder().session(s -> s.enabled(true)).build();
        channel = new EmbeddedChannel(
                new McpOperationHandler(server, new McpDispatcher(server, Runnable::run), Runnable::run));
    }

    @AfterEach
    void tearDown() {
        channel.close();
        server.close();
    }

    @Test
    void optionsReturns204() {
        sendOptions("http://localhost:3000");
        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.NO_CONTENT);
        response.release();
    }

    @Test
    void optionsRejectsMissingOrigin() {
        sendOptions(null);
        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.FORBIDDEN);
        response.release();
    }

    @Test
    void postNotificationReturns202() {
        server.createSession("sess-notif").activate();

        var body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Session-Id", "sess-notif")
                .set(HttpHeaderNames.ACCEPT, "application/json, text/event-stream");
        channel.writeInbound(request);

        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.ACCEPTED);
        response.release();
    }

    @Test
    void getWithoutSessionReturnsError() {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set(HttpHeaderNames.ACCEPT, "text/event-stream");
        channel.writeInbound(request);

        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        assertThat(response.content().toString(StandardCharsets.UTF_8)).contains("Missing MCP-Session-Id");
        response.release();
    }

    @Test
    void getWithValidSessionReturnsSseStream() {
        server.createSession("sess-get").activate();

        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set(HttpHeaderNames.ACCEPT, "text/event-stream")
                .set("MCP-Session-Id", "sess-get");
        channel.writeInbound(request);

        channel.runPendingTasks();
        var msg = channel.readOutbound();
        assertThat(msg).isInstanceOf(HttpResponse.class);
        var response = (HttpResponse) msg;
        assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat(response.headers().get(HttpHeaderNames.CONTENT_TYPE)).isEqualTo("text/event-stream");
    }

    @Test
    void idleOnSseStreamSendsHeartbeatAndKeepsChannelOpen() {
        server.createSession("sess-hb").activate();

        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set(HttpHeaderNames.ACCEPT, "text/event-stream")
                .set("MCP-Session-Id", "sess-hb");
        channel.writeInbound(request);
        channel.runPendingTasks();
        drainOutbound();

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        channel.runPendingTasks();

        assertThat(channel.isOpen()).as("SSE stream must survive an idle tick").isTrue();
        assertThat(readComment())
                .as("idle tick must emit an SSE comment heartbeat")
                .isEqualTo(":\r\n");

        // A second idle event produces another heartbeat (the handler is stateless across ticks).
        // Real-timer rescheduling is proven by SseHeartbeatE2eTest, not here.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        channel.runPendingTasks();
        assertThat(channel.isOpen()).isTrue();
        assertThat(readComment()).isEqualTo(":\r\n");
    }

    @Test
    void idleOnNonSseChannelClosesChannel() {
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        channel.runPendingTasks();

        assertThat(channel.isOpen())
                .as("plain idle keep-alive socket must close on idle")
                .isFalse();
    }

    @Test
    void deleteWithoutSessionReturnsError() {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE, "/mcp");
        request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");
        channel.writeInbound(request);

        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        response.release();
    }

    @Test
    void deleteWithValidSessionReturnsOk() {
        server.createSession("sess-del").activate();

        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE, "/mcp");
        request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000").set("MCP-Session-Id", "sess-del");
        channel.writeInbound(request);

        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);
        response.release();
    }

    @Test
    void postMalformedJsonReturnsParseError() {
        var body = "not json";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Session-Id", "sess-parse")
                .set(HttpHeaderNames.ACCEPT, "application/json, text/event-stream");
        channel.writeInbound(request);

        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        var content = response.content().toString(StandardCharsets.UTF_8);
        assertThat(content).contains("error");
        response.release();
    }

    @Test
    void unsupportedMethodReturns405() {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PATCH, "/mcp");
        request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");
        channel.writeInbound(request);

        var response = readResponse();
        assertThat(response.status()).isEqualTo(HttpResponseStatus.METHOD_NOT_ALLOWED);
        response.release();
    }

    private void sendOptions(@Nullable String origin) {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/mcp");
        if (origin != null) {
            request.headers().set(HttpHeaderNames.ORIGIN, origin);
        }
        channel.writeInbound(request);
    }

    private FullHttpResponse readResponse() {
        channel.runPendingTasks();
        var msg = channel.readOutbound();
        assertThat(msg).isInstanceOf(FullHttpResponse.class);
        return (FullHttpResponse) msg;
    }

    private void drainOutbound() {
        Object msg;
        while ((msg = channel.readOutbound()) != null) {
            io.netty.util.ReferenceCountUtil.release(msg);
        }
    }

    private String readComment() {
        var msg = channel.readOutbound();
        assertThat(msg).isInstanceOf(HttpContent.class);
        var content = (HttpContent) msg;
        try {
            return content.content().toString(StandardCharsets.UTF_8);
        } finally {
            content.release();
        }
    }
}
