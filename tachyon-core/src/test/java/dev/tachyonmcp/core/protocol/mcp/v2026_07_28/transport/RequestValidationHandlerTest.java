/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.McpProtocol;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.transport.netty.ProtocolVersionHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A raw HTTP client can't transmit the 0x80-0xFF/control-character bytes SEP-2243 forbids in an
 * {@code Mcp-Param-*} header (a real client either rejects them at {@code header()} time or
 * substitutes {@code '?'}), so this exercises {@link RequestValidationHandler} directly with a
 * hand-built header value instead of going through an HTTP client, per the sibling
 * {@code McpHeaderGuardHandlerTest}/{@code ProtocolVersionHandlerTest} pattern.
 */
class RequestValidationHandlerTest {

    private final EmbeddedChannel channel = new EmbeddedChannel(
            new ProtocolVersionHandler("/mcp"), new RequestValidationHandler(mock(ServerEngine.class)));

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    private static DefaultFullHttpRequest notificationRequest(String body) {
        var req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        req.headers().set("MCP-Protocol-Version", McpProtocol.VERSION);
        req.headers().set("Mcp-Method", "notifications/cancelled");
        return req;
    }

    private @Nullable HttpResponseStatus rejectionStatus() {
        Object out = channel.readOutbound();
        try {
            return out instanceof HttpResponse resp ? resp.status() : null;
        } finally {
            ReferenceCountUtil.release(out);
        }
    }

    /** Mcp-Method is required on notifications too (this revision defines notifications/cancelled). */
    @Test
    void rejectsNotificationWithMismatchedMethodHeader() {
        var req = notificationRequest(
                "{\"jsonrpc\": \"2.0\", \"method\": \"notifications/cancelled\", \"params\": {\"requestId\": \"1\"}}");
        req.headers().set("Mcp-Method", "notifications/other");

        channel.writeInbound(req);

        assertThat(rejectionStatus()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
    }

    /** Not scoped to tools/call: the char-format rule applies to notifications too. */
    @Test
    void rejectsNotificationWithInvalidCharacterParamHeader() {
        var req = notificationRequest(
                "{\"jsonrpc\": \"2.0\", \"method\": \"notifications/cancelled\", \"params\": {\"requestId\": \"1\"}}");
        req.headers().set("Mcp-Param-Reason", "us-westé");

        channel.writeInbound(req);

        assertThat(rejectionStatus()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
    }

    @Test
    void acceptsNotificationWithoutParamHeaders() {
        var req = notificationRequest(
                "{\"jsonrpc\": \"2.0\", \"method\": \"notifications/cancelled\", \"params\": {\"requestId\": \"1\"}}");

        channel.writeInbound(req);

        assertThat(rejectionStatus()).isNull();
    }
}
