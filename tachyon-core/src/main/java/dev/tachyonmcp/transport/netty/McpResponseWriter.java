/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.mcp.McpHeaderNames;
import dev.tachyonmcp.server.domain.RequestId;
import dev.tachyonmcp.server.domain.ServerErrors;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import org.jspecify.annotations.Nullable;

public final class McpResponseWriter {

    private McpResponseWriter() {}

    public static void sendOptions(ChannelHandlerContext ctx, @Nullable String origin) {
        if (origin == null || origin.isEmpty()) {
            ChannelHandlerUtils.sendPlainTextAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Origin Required");
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
        // Signal close so the client does not pool this socket; HttpServerKeepAliveHandler
        // appends `Connection: close` and closes the channel after this response.
        HttpUtil.setKeepAlive(response, false);
        ctx.writeAndFlush(response);
    }

    public static ChannelFuture sendJsonResponse(
            ChannelHandlerContext ctx, byte[] body, @Nullable String sessionId, @Nullable String origin) {
        return sendJsonResponse(ctx, body, HttpResponseStatus.OK, false, sessionId, origin);
    }

    public static ChannelFuture sendJsonResponse(
            ChannelHandlerContext ctx,
            byte[] body,
            HttpResponseStatus status,
            @Nullable String sessionId,
            @Nullable String origin) {
        return sendJsonResponse(ctx, body, status, false, sessionId, origin);
    }

    public static ChannelFuture sendJsonResponse(
            ChannelHandlerContext ctx,
            byte[] body,
            HttpResponseStatus status,
            boolean close,
            @Nullable String sessionId,
            @Nullable String origin) {
        // Zero-copy wrap on the event loop: the byte[] is GC-managed until this point, so a
        // dropped task on the shutdown path is plain garbage, never a pooled-buffer leak.
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(body));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        if (sessionId != null) {
            response.headers().set(McpHeaderNames.MCP_SESSION_ID, sessionId);
        }
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        // HttpServerKeepAliveHandler appends `Connection: close` and closes the channel after
        // this response when keep-alive is disabled.
        HttpUtil.setKeepAlive(response, !close);
        return ctx.writeAndFlush(response);
    }

    /**
     * Writes a JSON-RPC internal-error response and closes the connection, for a dispatch failure
     * that occurred after a POST-SSE stream had already started (so the normal response path can no
     * longer send a result).
     *
     * @param ctx    the channel to write the response on
     * @param id     the id of the request that failed
     * @param origin the request's {@code Origin} header value, echoed via CORS headers, or {@code null}
     * @param mapper the protocol response mapper used to encode the error
     * @return the future for the write
     */
    public static ChannelFuture sendInternalError(
            ChannelHandlerContext ctx, RequestId id, @Nullable String origin, ProtocolResponseMapper mapper) {
        var error = mapper.error(ServerErrors.internalError("Internal error"));
        var body = JsonRpcCodec.serializeError(id, error.code(), error.message(), error.data());
        return sendJsonResponse(ctx, body, HttpResponseStatus.valueOf(error.httpStatus()), true, null, origin);
    }
}
