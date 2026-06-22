/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.runtime.McpHeaderNames;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.Nullable;

public final class McpResponseWriter {

    private McpResponseWriter() {}

    public static void sendOptions(ChannelHandlerContext ctx, @Nullable String origin) {
        if (origin == null || origin.isEmpty()) {
            sendPlainText(ctx, HttpResponseStatus.FORBIDDEN, "Origin Required")
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        response.headers()
                .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, DELETE, OPTIONS")
                .set(
                        HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                        String.join(
                                ", ",
                                McpHeaderNames.MCP_PROTOCOL_VERSION,
                                McpHeaderNames.MCP_SESSION_ID,
                                McpHeaderNames.LAST_EVENT_ID,
                                HttpHeaderNames.CONTENT_TYPE.toString(),
                                HttpHeaderNames.ORIGIN.toString()))
                .set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "86400");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public static ChannelFuture sendJsonResponse(
            ChannelHandlerContext ctx, ByteBuf body, @Nullable String sessionId, @Nullable String origin) {
        return sendJsonResponse(ctx, body, false, sessionId, origin);
    }

    public static ChannelFuture sendJsonResponse(
            ChannelHandlerContext ctx,
            ByteBuf body,
            boolean close,
            @Nullable String sessionId,
            @Nullable String origin) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());
        if (close) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }
        if (sessionId != null) {
            response.headers().set(McpHeaderNames.MCP_SESSION_ID, sessionId);
        }
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        final ChannelFuture channelFuture = ctx.writeAndFlush(response);
        if (close) {
            return channelFuture.addListener(ChannelFutureListener.CLOSE);
        } else {
            return channelFuture;
        }
    }

    public static ChannelFuture sendInternalError(ChannelHandlerContext ctx, Object id, @Nullable String origin) {
        var body = JsonRpcCodec.serializeError(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error", null);
        return sendJsonResponse(ctx, body, true, null, origin);
    }

    private static ChannelFuture sendPlainText(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        var response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, ByteBufUtil.writeUtf8(ctx.alloc(), message));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers()
                .set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return ctx.writeAndFlush(response);
    }
}
