/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces MCP Accept-header rules per spec (`MUST include application/json, text/event-stream`
 * on POST; `MUST include text/event-stream` on GET). Returns 406 Not Acceptable on violation.
 */
@Sharable
public final class AcceptValidationHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AcceptValidationHandler.class);

    private final String mcpEndpoint;

    public AcceptValidationHandler(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
    }

    static final String[] POST_ACCEPT_TYPES = {"application/json", "text/event-stream"};
    static final String[] GET_ACCEPT_TYPES = {"text/event-stream"};

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest req && req.uri().startsWith(mcpEndpoint)) {
            var method = req.method();
            if (method == HttpMethod.POST) {
                if (!isAcceptable(req, POST_ACCEPT_TYPES)) {
                    reject(ctx, req, "POST", POST_ACCEPT_TYPES);
                    return;
                }
            } else if (method == HttpMethod.GET) {
                if (!isAcceptable(req, GET_ACCEPT_TYPES)) {
                    reject(ctx, req, "GET", GET_ACCEPT_TYPES);
                    return;
                }
            }
        }
        ctx.fireChannelRead(msg);
    }

    private static boolean isAcceptable(FullHttpRequest req, String[] requiredTypes) {
        var accept = req.headers().get(HttpHeaderNames.ACCEPT);
        if (accept == null || accept.isBlank()) return false;
        if (accept.contains("*/*")) return true;
        for (var required : requiredTypes) {
            if (accept.contains(required)) return true;
        }
        return false;
    }

    private static void reject(ChannelHandlerContext ctx, FullHttpRequest req, String method, String[] requiredTypes) {
        logger.warn(
                "MCP client {} Accept header on {}; required '{}'",
                req.headers().get(HttpHeaderNames.ACCEPT) == null ? "missing" : "incompatible",
                method,
                String.join(", ", requiredTypes));
        var msg = "Accept header must include " + String.join(" or ", requiredTypes) + " on " + method;
        var body = Unpooled.copiedBuffer(msg, StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_ACCEPTABLE, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        var origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        req.release();
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
