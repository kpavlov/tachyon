/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProtocolVersionHandlerTest {

    private final EmbeddedChannel channel = new EmbeddedChannel(new ProtocolVersionHandler("/mcp"));

    @AfterEach
    void tearDown() {
        channel.close();
    }

    @Test
    void unsupportedVersionReturnsBadRequest() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Protocol-Version", "2099-01-01");
        channel.writeInbound(request);

        var response = channel.readOutbound();
        assertThat(response).isInstanceOf(FullHttpResponse.class);
        var r = (FullHttpResponse) response;
        assertThat(r.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        var content = r.content().toString(StandardCharsets.UTF_8);
        assertThat(content).contains("Unsupported protocol version");
        r.release();
    }

    @Test
    void unsupportedVersionRejectsNonMcpUri() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/other", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Protocol-Version", "2099-01-01");
        channel.writeInbound(request);

        assertThat((Object) channel.readOutbound()).isNull();
    }

    @Test
    void supportedVersionPassesThrough() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Protocol-Version", "2025-11-25");
        channel.writeInbound(request);

        assertThat((Object) channel.readOutbound()).isNull();
    }

    @Test
    void missingVersionPassesThrough() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");
        channel.writeInbound(request);

        assertThat((Object) channel.readOutbound()).isNull();
    }

    @Test
    void nonPostMethodPassesThrough() {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Protocol-Version", "2099-01-01");
        channel.writeInbound(request);

        assertThat((Object) channel.readOutbound()).isNull();
    }

    @Test
    void unsupportedVersionEchoesCorsOrigin() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.ORIGIN, "http://example.com").set("MCP-Protocol-Version", "2099-01-01");
        channel.writeInbound(request);

        var response = (FullHttpResponse) channel.readOutbound();
        assertThat(response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://example.com");
        response.release();
    }
}
