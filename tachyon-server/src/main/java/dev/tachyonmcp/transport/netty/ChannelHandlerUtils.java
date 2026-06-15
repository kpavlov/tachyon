/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.InteractionHandler.INTERACTION_CONTEXT_KEY;

import dev.tachyonmcp.runtime.InteractionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ChannelHandlerUtils {

    private ChannelHandlerUtils() {}

    public static InteractionContext interactionContext(ChannelHandlerContext ctx) {
        return Objects.requireNonNull(
                ctx.channel().attr(INTERACTION_CONTEXT_KEY).get(),
                "InteractionContext is null. Check if InteractionHandler is configured correctly.");
    }

    public static void sendAccepted(ChannelHandlerContext ctx, @Nullable String origin) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        ctx.writeAndFlush(response);
    }

    public static ChannelFuture sendPlainText(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        return sendPlainText(ctx, status, message, null);
    }

    public static ChannelFuture sendPlainText(
            ChannelHandlerContext ctx, HttpResponseStatus status, String message, @Nullable String origin) {
        return sendResponse(ctx, status, "text/plain", ByteBufUtil.writeUtf8(ctx.alloc(), message), origin);
    }

    public static ChannelFuture sendResponse(
            ChannelHandlerContext ctx, HttpResponseStatus status, String contentType, ByteBuf body) {
        return sendResponse(ctx, status, contentType, body, null);
    }

    public static ChannelFuture sendResponse(
            ChannelHandlerContext ctx,
            HttpResponseStatus status,
            String contentType,
            ByteBuf body,
            @Nullable String origin) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        return ctx.writeAndFlush(response);
    }
}
