/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2026_07_28.McpProtocol;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
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
    void unsupportedVersionFlagsRequestAndPassesThrough() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Protocol-Version", "2099-01-01");
        channel.writeInbound(request);

        // No response yet: rejection is deferred to UnsupportedProtocolVersionHandler, which runs
        // after http-aggregator so it can echo the request's JSON-RPC id.
        assertThat((Object) channel.readOutbound()).isNull();
        assertThat(channel.attr(ProtocolVersionHandler.UNSUPPORTED_VERSION_KEY).get())
                .isEqualTo("2099-01-01");
        channel.finishAndReleaseAll();
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
        assertThat(channel.attr(ProtocolVersionHandler.UNSUPPORTED_VERSION_KEY).get())
                .isNull();
        channel.finishAndReleaseAll();
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
        channel.finishAndReleaseAll();
    }

    @Test
    void latestVersionBindsItsProtocolToTheInteraction() {
        var negotiationChannel = new EmbeddedChannel(new ProtocolVersionHandler("/mcp"), new InteractionHandler());
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp");
        request.headers().set("MCP-Protocol-Version", McpProtocol.VERSION);

        negotiationChannel.writeInbound(request);

        assertThat(negotiationChannel
                        .attr(InteractionHandler.INTERACTION_CONTEXT_KEY)
                        .get())
                .extracting(context -> context.protocolVersion())
                .isEqualTo(McpProtocol.VERSION);
        negotiationChannel.finishAndReleaseAll();
    }

    @Test
    void missingVersionPassesThrough() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");
        channel.writeInbound(request);

        assertThat((Object) channel.readOutbound()).isNull();
        channel.finishAndReleaseAll();
    }

    @Test
    void missingVersionOnUnregisteredEndpointFlagsRequest() {
        var customChannel = new EmbeddedChannel(new ProtocolVersionHandler("/custom"));
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/custom");

        customChannel.writeInbound(request);

        assertThat((Object) customChannel.readOutbound()).isNull();
        assertThat(customChannel
                        .attr(ProtocolVersionHandler.UNSUPPORTED_VERSION_KEY)
                        .get())
                .isEqualTo("");
        customChannel.finishAndReleaseAll();
    }

    @Test
    void nonPostMethodPassesThrough() {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp");
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set("MCP-Protocol-Version", "2099-01-01");
        channel.writeInbound(request);

        assertThat((Object) channel.readOutbound()).isNull();
        channel.finishAndReleaseAll();
    }
}
